package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class PedidoRecepcionProductoResolver implements GraphQLResolver<PedidoRecepcionProductoDto> {

    public PedidoRecepcionProductoEstado estado(PedidoRecepcionProductoDto e) {
        if (e.getTotalCantidadRecibidaPorUnidad() == null) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        } else if (e.getTotalCantidadRecibidaPorUnidad() >= e.getTotalCantidadARecibirPorUnidad()) {
            return PedidoRecepcionProductoEstado.RECIBIDO;
        } else {
            return PedidoRecepcionProductoEstado.RECIBIDO_PARCIALMENTE;
        }
    }

}
