package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.PedidoItemSucursal;
import com.franco.dev.service.operaciones.MovimientoStockService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PedidoItemSucursalResolver implements GraphQLResolver<PedidoItemSucursal> {

    @Autowired
    private MovimientoStockService movimientoStockService;

    public Double stockDisponible(PedidoItemSucursal pedidoItemSucursal) {
        if (pedidoItemSucursal.getPedidoItem() == null || 
            pedidoItemSucursal.getPedidoItem().getProducto() == null || 
            pedidoItemSucursal.getSucursal() == null) {
            return 0.0;
        }

        Long productoId = pedidoItemSucursal.getPedidoItem().getProducto().getId();
        Long sucursalId = pedidoItemSucursal.getSucursal().getId();

        Float stock = movimientoStockService.getRepository().stockByProductoIdAndSucursalId(productoId, sucursalId);

        return stock != null && stock != 0.0 ? stock : 0.0;
    }
} 