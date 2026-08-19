package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import graphql.kickstart.tools.GraphQLResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Campos derivados de {@link MovimientoCajaVirtual}.
 *
 * <p>{@code esPagoConsolidado} contesta si el movimiento es el asiento de un evento de pago del
 * motor de CPP. <b>No se puede deducir del {@code origenTipo}</b>: los origenes de RRHH
 * (vale, liquidacion, finiquito, aguinaldo) los postean tanto el motor como el egreso directo
 * viejo de cada modulo, asi que el mismo valor significa dos cosas distintas. La verdad la
 * tiene {@code PagoSolicitudDetalle}, que apunta al movimiento del evento.</p>
 */
@Component
@AllArgsConstructor
public class MovimientoCajaVirtualFieldResolver implements GraphQLResolver<MovimientoCajaVirtual> {

    private final PagoSolicitudDetalleRepository detalleRepository;

    public Boolean esPagoConsolidado(MovimientoCajaVirtual mov) {
        if (mov == null || mov.getId() == null) return false;
        return detalleRepository.existsByMovimientoCajaVirtualId(mov.getId());
    }
}
