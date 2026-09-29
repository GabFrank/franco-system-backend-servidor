package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.rrhh.AguinaldoRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.rrhh.LiquidacionFinalService;
import com.franco.dev.service.rrhh.LiquidacionSueldoService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Anula una liquidacion mensual o un finiquito <b>junto con su pago de tesoreria</b>.
 *
 * <p>Pagados desde el hub de la caja, la plata salio por un evento de pago ({@code Pago}) que puede ser
 * de caja, banco o cheque. Anular el documento solo, como hacia {@code anular}, no devolvia la plata de
 * un pago bancario ni revertia vales/cuotas/aguinaldo, y en efectivo dejaba el pago vivo (anularlo
 * despues revertia la caja dos veces). Aca se anula el pago con {@link PagoProveedorService#anularPagoCpp},
 * que devuelve la plata por el medio usado y, via {@code sincronizarDesdeSolicitudPago}, deja el
 * documento APROBADO con los efectos revertidos; recien ahi se lo anula.</p>
 *
 * <p>Un pago que tambien pago otras obligaciones (lote) no se anula desde aca: anularlo reabriria a
 * todas. Se rechaza nombrandolas. Todo corre en una transaccion y sobre la <b>misma instancia</b>
 * gestionada del documento (OSIV): {@code anular} tiene que verla ya APROBADA.</p>
 */
@Service
@AllArgsConstructor
public class AnulacionPagoRrhhService {

    private final PagoProveedorService pagoProveedorService;
    private final PagoService pagoService;
    private final PagoSolicitudDetalleRepository detalleRepository;
    private final LiquidacionSueldoService liquidacionSueldoService;
    private final LiquidacionFinalService liquidacionFinalService;
    private final LiquidacionSueldoRepository liquidacionSueldoRepository;
    private final LiquidacionFinalRepository liquidacionFinalRepository;
    private final ValeRepository valeRepository;
    private final AguinaldoRepository aguinaldoRepository;
    private final TesoreriaSecurityService tesoreriaSecurity;

    @Transactional
    public LiquidacionSueldo anularLiquidacion(Long id) {
        // Lock primero: dos anulaciones simultaneas se serializan y la segunda ve ANULADA.
        LiquidacionSueldo liq = liquidacionSueldoRepository.lockById(id)
                .orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));
        if (liq.getEstado() != LiquidacionSueldoEstado.PAGADA || liq.getSolicitudPagoId() == null) {
            return liquidacionSueldoService.anular(id);
        }
        String documento = "la liquidacion #" + id;
        List<Long> pagos = pagosVivos(liq.getSolicitudPagoId(), documento);
        if (pagos.isEmpty()) return liquidacionSueldoService.anularSinPagoVivo(id);
        anularPagos(pagos, "ANULACION LIQUIDACION #" + id);
        return liquidacionSueldoService.anular(id);
    }

    @Transactional
    public LiquidacionFinal anularFiniquito(Long id) {
        LiquidacionFinal lf = liquidacionFinalRepository.lockById(id)
                .orElseThrow(() -> new GraphQLException("Liquidacion final no encontrada"));
        if (lf.getEstado() != LiquidacionFinalEstado.PAGADA || lf.getSolicitudPagoId() == null) {
            return liquidacionFinalService.anular(id);
        }
        String documento = "el finiquito #" + id;
        List<Long> pagos = pagosVivos(lf.getSolicitudPagoId(), documento);
        if (pagos.isEmpty()) return liquidacionFinalService.anularSinPagoVivo(id);
        anularPagos(pagos, "ANULACION LIQUIDACION FINAL #" + id);
        return liquidacionFinalService.anular(id);
    }

    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Los pagos no cancelados que tocaron la solicitud del documento. Rechaza si alguno tambien pago otra
     * solicitud: la exclusividad se mira sobre <b>todos</b> los detalles de cada pago, no solo los de esta
     * solicitud, porque un evento puede mezclar liquidaciones, vales, gastos y proveedores.
     */
    private List<Long> pagosVivos(Long solicitudPagoId, String documento) {
        Set<Long> pagoIds = new LinkedHashSet<>();
        for (PagoSolicitudDetalle d : detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(solicitudPagoId)) {
            if (Boolean.TRUE.equals(d.getAnulado()) || d.getPagoId() == null) continue;
            Pago pago = pagoService.findById(d.getPagoId()).orElse(null);
            if (pago == null || pago.getEstado() == PagoEstado.CANCELADO) continue;
            pagoIds.add(d.getPagoId());
        }
        if (pagoIds.isEmpty()) return new ArrayList<>();

        // Anular un pago de tesoreria desde RRHH no puede ser un atajo para quien no tiene el rol de
        // tesoreria que exige el mismo boton en la caja.
        tesoreriaSecurity.requirePagarCpp();

        for (Long pagoId : pagoIds) {
            Set<Long> otras = new LinkedHashSet<>();
            for (PagoSolicitudDetalle d : detalleRepository.findByPagoIdOrderByCreadoEnAsc(pagoId)) {
                if (Boolean.TRUE.equals(d.getAnulado())) continue;
                if (!Objects.equals(d.getSolicitudPagoId(), solicitudPagoId)) otras.add(d.getSolicitudPagoId());
            }
            if (!otras.isEmpty()) {
                List<String> nombres = new ArrayList<>();
                for (Long sp : otras) nombres.add(describir(sp));
                throw new GraphQLException("No se puede anular " + documento + " sola: el pago #" + pagoId
                        + " es un lote que tambien pago " + String.join(", ", nombres)
                        + ". Anula el pago completo desde tesoreria.");
            }
        }
        return new ArrayList<>(pagoIds);
    }

    private void anularPagos(List<Long> pagoIds, String motivo) {
        Usuario usuario = tesoreriaSecurity.currentUsuario();
        for (Long pagoId : pagoIds) {
            pagoProveedorService.anularPagoCpp(pagoId, motivo, usuario);
        }
    }

    /** "la liquidacion #12 (JUAN PEREZ)", "el vale #3 (...)", o la solicitud si no es de RRHH. */
    private String describir(Long solicitudPagoId) {
        LiquidacionSueldo l = liquidacionSueldoRepository.findBySolicitudPagoId(solicitudPagoId);
        if (l != null) return "la liquidacion #" + l.getId() + nombre(l.getFuncionario());
        LiquidacionFinal f = liquidacionFinalRepository.findBySolicitudPagoId(solicitudPagoId);
        if (f != null) return "el finiquito #" + f.getId() + nombre(f.getFuncionario());
        Vale v = valeRepository.findBySolicitudPagoId(solicitudPagoId);
        if (v != null) return "el vale #" + v.getId() + nombre(v.getFuncionario());
        Aguinaldo a = aguinaldoRepository.findBySolicitudPagoId(solicitudPagoId);
        if (a != null) return "el aguinaldo #" + a.getId() + nombre(a.getFuncionario());
        return "la solicitud de pago #" + solicitudPagoId;
    }

    private static String nombre(Funcionario f) {
        return f != null && f.getPersona() != null && f.getPersona().getNombre() != null
                ? " (" + f.getPersona().getNombre() + ")" : "";
    }
}
