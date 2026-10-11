package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import graphql.kickstart.tools.GraphQLResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Campos derivados de {@link MovimientoBancario}.
 *
 * <p>{@code pagoId} contesta a que evento de pago del motor de CPP pertenece el movimiento.
 * <b>No se puede leer del {@code origenId}</b>: la pata bancaria consolidada lo lleva, pero el
 * cheque al contado de un pago se registra con {@code origenId} nulo, y un movimiento de pago
 * anterior al motor no pertenece a ningun evento. La verdad la tiene
 * {@code PagoSolicitudDetalle}, que apunta al movimiento.</p>
 */
@Component
@AllArgsConstructor
public class MovimientoBancarioFieldResolver implements GraphQLResolver<MovimientoBancario> {

    private final PagoSolicitudDetalleRepository detalleRepository;

    public Long pagoId(MovimientoBancario mov) {
        if (mov == null || mov.getId() == null) return null;
        return detalleRepository.findFirstByMovimientoBancarioId(mov.getId())
                .map(PagoSolicitudDetalle::getPagoId)
                .orElse(null);
    }
}
