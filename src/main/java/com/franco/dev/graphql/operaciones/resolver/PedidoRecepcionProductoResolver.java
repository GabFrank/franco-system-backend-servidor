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
        
        Double cantidadRecibida = e.getTotalCantidadRecibidaPorUnidad() != null ? e.getTotalCantidadRecibidaPorUnidad() : 0.0;
        Double cantidadRechazada = e.getTotalCantidadRechazadaPorUnidad() != null ? e.getTotalCantidadRechazadaPorUnidad() : 0.0;
        Double cantidadARecibir = e.getTotalCantidadARecibirPorUnidad() != null ? e.getTotalCantidadARecibirPorUnidad() : 0.0;
        
        // Calcular total procesado (recibido + rechazado)
        Double totalProcesado = cantidadRecibida + cantidadRechazada;
        
        // Si no hay cantidad procesada (recibida o rechazada), está pendiente
        if (totalProcesado == 0.0) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        // Si no hay cantidad a recibir o es 0, está pendiente
        if (cantidadARecibir == null || cantidadARecibir == 0.0) {
            return PedidoRecepcionProductoEstado.PENDIENTE;
        }
        
        // Si el total procesado (recibido + rechazado) es mayor o igual a la cantidad a recibir, está recibido
        if (totalProcesado >= cantidadARecibir) {
            return PedidoRecepcionProductoEstado.RECIBIDO;
        }
        
        // Si hay cantidad procesada pero es menor a la cantidad a recibir, está recibido parcialmente
        return PedidoRecepcionProductoEstado.RECIBIDO_PARCIALMENTE;
    }

}
