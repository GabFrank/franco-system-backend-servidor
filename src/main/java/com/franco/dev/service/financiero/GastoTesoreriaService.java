package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final SolicitudPagoService solicitudPagoService;
    private final TipoGastoService tipoGastoService;
    private final MonedaService monedaService;
    private final ProveedorService proveedorService;

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
}
