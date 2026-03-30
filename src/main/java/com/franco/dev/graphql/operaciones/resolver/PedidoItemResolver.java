package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.service.operaciones.PedidoItemDistribucionService;
import com.franco.dev.service.operaciones.PedidoItemService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PedidoItemResolver implements GraphQLResolver<PedidoItem> {
    
    @Autowired
    private PedidoItemDistribucionService pedidoItemDistribucionService;
    
    @Autowired
    private PedidoItemService pedidoItemService;
    
    /**
     * Resolver para el campo distribucionConcluida
     * @param pedidoItem El pedido item
     * @return true si la distribución está concluida, false si está pendiente
     */
    public Boolean distribucionConcluida(PedidoItem pedidoItem) {
        if (pedidoItem == null || pedidoItem.getId() == null || pedidoItem.getCantidadSolicitada() == null) {
            return false;
        }
        
        return pedidoItemDistribucionService.isDistribucionConcluida(
            pedidoItem.getId(), 
            pedidoItem.getCantidadSolicitada()
        );
    }

    /**
     * Resolver para el campo cantidadPendiente
     * @param pedidoItem El pedido item
     * @return Cantidad pendiente de conciliar
     */
    public Double cantidadPendiente(PedidoItem pedidoItem) {
        if (pedidoItem == null || pedidoItem.getId() == null) {
            return 0.0;
        }
        
        return pedidoItemService.getCantidadPendiente(pedidoItem.getId());
    }
}