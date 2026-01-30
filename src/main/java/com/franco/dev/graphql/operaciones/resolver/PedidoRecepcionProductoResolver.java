package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class PedidoRecepcionProductoResolver implements GraphQLResolver<PedidoRecepcionProductoDto> {

    public PedidoRecepcionProductoEstado estado(PedidoRecepcionProductoDto e) {
        if (e == null) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        Double cantidadRecibida = e.getTotalCantidadRecibidaPorUnidad();
        Double cantidadARecibir = e.getTotalCantidadARecibirPorUnidad();
        
        // Si no hay cantidad recibida o es 0, está pendiente
        if (cantidadRecibida == null || cantidadRecibida == 0.0) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        // Si no hay cantidad a recibir o es 0, está pendiente
        if (cantidadARecibir == null || cantidadARecibir == 0.0) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        // Si la cantidad recibida es mayor o igual a la cantidad a recibir, está recibido
        if (cantidadRecibida >= cantidadARecibir) {
            return PedidoRecepcionProductoEstado.RECIBIDO;
        }
        
        // Si hay cantidad recibida pero es menor a la cantidad a recibir, está recibido parcialmente
        return PedidoRecepcionProductoEstado.RECIBIDO_PARCIALMENTE;
    }

}
