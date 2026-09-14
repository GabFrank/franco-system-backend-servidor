package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.utilitarios.IdCentral;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Alta de un gasto para pagar (Opción B, consolidado): el gasto vive entero en una
 * {@link SolicitudPago} de tipo GASTO (categoría + descripción + beneficiario opcional +
 * vencimiento), en estado SOLICITADO, pagable con el <b>mismo</b> motor de CPP.
 *
 * <p>La deuda pagable unificada (SolicitudPago) es tanto el documento del gasto como su
 * obligación de pago — un solo registro, un solo motor de pago, extendido por {@code tipo}.</p>
 */
@Service
@AllArgsConstructor
public class GastoTesoreriaService {

    /**
     * Sucursal 0 (SERVIDOR). Los gastos de caja mayor no pertenecen a ninguna sucursal fisica.
     * Ojo que no es "SUC. CENTRAL", que es la filial 1.
     */
    public static final Long SUCURSAL_SERVIDOR = 0L;

    private static final Long MONEDA_GUARANI = 1L;
    private static final Long MONEDA_REAL = 2L;
    private static final Long MONEDA_DOLAR = 3L;

    private final SolicitudPagoService solicitudPagoService;
    private final TipoGastoService tipoGastoService;
    private final MonedaService monedaService;
    private final ProveedorService proveedorService;
    private final GastoRepository gastoRepository;
    private final GastoService gastoService;

    @Transactional
    public SolicitudPago crearGastoParaPago(Long tipoGastoId, String descripcion, Long monedaId, Double monto,
                                            Long beneficiarioProveedorId, Long beneficiarioPersonaId,
                                            LocalDateTime fechaVencimiento, Long sucursalId, Usuario usuario) {
        if (monto == null || monto <= 0) {
            throw new GraphQLException("El monto del gasto debe ser positivo");
        }
        if (descripcion == null || descripcion.trim().isEmpty()) {
            throw new GraphQLException("La descripción del gasto es requerida");
        }
        if (tipoGastoId == null) {
            throw new GraphQLException("La categoría del gasto es obligatoria");
        }
        TipoGasto categoria = tipoGastoService.findById(tipoGastoId)
                .orElseThrow(() -> new GraphQLException("Categoría de gasto no encontrada"));
        Moneda moneda = monedaService.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada"));
        Proveedor beneficiario = beneficiarioProveedorId != null
                ? proveedorService.findById(beneficiarioProveedorId).orElse(null) : null;

        return solicitudPagoService.crearSolicitudGasto(
                beneficiario, categoria, moneda, monto, descripcion.trim(), fechaVencimiento, usuario);
    }

    /**
     * Refleja en {@code financiero.gasto} el estado de pago de una solicitud de tipo GASTO, para
     * que el gasto pagado desde la caja mayor aparezca en el grafico "Gastos por Categoria" y en
     * la lista de gastos: las dos leen esa tabla, no {@code operaciones.solicitud_pago}.
     *
     * <p>El gasto materializado vive en la <b>sucursal 0</b> (SERVIDOR), sin caja ni
     * responsable, y queda vinculado a su solicitud por {@code solicitud_pago_id} (unico). Es un
     * espejo de solo lectura: la fuente de verdad de la deuda sigue siendo la solicitud.</p>
     *
     * <p>Lo invoca el motor de pago en los dos sentidos, igual que
     * {@code PreGastoService.sincronizarDesdeSolicitudPago}: al pagar se crea o se actualiza con
     * lo efectivamente pagado, y al anular el pago se marca cancelado (no se borra: asi el numero
     * de gasto no se reusa y queda la trazabilidad). No-op para compras y para RRHH.</p>
     */
    @Transactional
    public void sincronizarDesdeSolicitudPago(SolicitudPago sp) {
        if (sp == null || sp.getTipo() != TipoSolicitudPago.GASTO) return;

        BigDecimal pagado = sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO;
        Gasto gasto = gastoRepository.findFirstBySolicitudPagoId(sp.getId());

        // Anulacion: la solicitud vuelve a deber todo. El gasto se cancela, no se borra.
        if (pagado.signum() <= 0) {
            if (gasto != null && !Boolean.TRUE.equals(gasto.getCancelado())) {
                gasto.setCancelado(true);
                gastoRepository.save(gasto);
            }
            return;
        }

        if (gasto == null) {
            gasto = new Gasto();
            gasto.setId(siguienteIdServidor());
            gasto.setSucursalId(SUCURSAL_SERVIDOR);
            gasto.setSolicitudPagoId(sp.getId());
            gasto.setActivo(true);
            gasto.setFinalizado(true);
        }

        // Una anulacion parcial (quedo saldo pagado) rehabilita el gasto por el monto que sigue en pie.
        gasto.setCancelado(false);
        gasto.setTipoGasto(sp.getTipoGasto());
        gasto.setUsuario(sp.getUsuario());
        gasto.setObservacion(observacionDe(sp));
        aplicarMonto(gasto, sp, pagado.doubleValue());

        gastoService.save(gasto);
    }

    /**
     * Id correlativo dentro de la sucursal 0, igual que el resto de las entidades de clave
     * compuesta, pero siempre IMPAR: en el central el trigger rechazar_id_de_filial (migracion
     * V223.1) rechaza todo INSERT en financiero.gasto con id par, porque los pares los genera
     * cada filial y chocarian al replicarse. Es el mismo criterio que usa DevolucionService al
     * materializar su gasto.
     */
    private Long siguienteIdServidor() {
        return IdCentral.siguienteImpar(gastoRepository.findMaxId(SUCURSAL_SERVIDOR));
    }

    /**
     * El monto va a la columna de su moneda, como cualquier gasto de caja. Los reportes agregados
     * suman {@code retiro_gs}, asi que un gasto en dolares o reales se ve en la lista pero no en
     * el grafico -- es exactamente lo que ya pasa con los gastos de caja fisica en otra moneda.
     */
    private void aplicarMonto(Gasto gasto, SolicitudPago sp, Double monto) {
        Long monedaId = sp.getMoneda() != null ? sp.getMoneda().getId() : MONEDA_GUARANI;
        gasto.setRetiroGs(MONEDA_GUARANI.equals(monedaId) ? monto : 0.0);
        gasto.setRetiroRs(MONEDA_REAL.equals(monedaId) ? monto : 0.0);
        gasto.setRetiroDs(MONEDA_DOLAR.equals(monedaId) ? monto : 0.0);
        gasto.setVueltoGs(0.0);
        gasto.setVueltoRs(0.0);
        gasto.setVueltoDs(0.0);
    }

    /**
     * Misma descripcion que muestra el movimiento de la caja mayor ("#446 - BENEFICIARIO - detalle"),
     * menos la categoria, que en el gasto es una columna propia.
     */
    private String observacionDe(SolicitudPago sp) {
        String beneficiario = "\u2014";
        if (sp.getProveedor() != null && sp.getProveedor().getPersona() != null
                && sp.getProveedor().getPersona().getNombre() != null) {
            beneficiario = sp.getProveedor().getPersona().getNombre();
        }
        String detalle = sp.getObservaciones() != null ? sp.getObservaciones() : "";
        return "#" + sp.getId() + " - " + beneficiario + " - " + detalle;
    }
}
