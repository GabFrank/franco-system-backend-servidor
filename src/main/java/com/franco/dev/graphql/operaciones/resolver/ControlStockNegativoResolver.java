package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.ControlStockNegativo;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlStockNegativoResolver implements GraphQLResolver<ControlStockNegativo> {

    @Autowired
    private SucursalService sucursalService;

    public Sucursal sucursal(ControlStockNegativo c) {
        return c.getSucursalId() != null ? sucursalService.findById(c.getSucursalId()).orElse(null) : null;
    }

    @Autowired
    private MovimientoStockService movimientoStockService;

    /**
     * Stock de hoy del producto en la sucursal del registro. Se calcula solo si el cliente pide el
     * campo. Sin producto o sin sucursal devuelve null: "no pude calcularlo" no es "hay 0".
     */
    public Double stockActual(ControlStockNegativo c) {
        if (c.getProducto() == null || c.getProducto().getId() == null || c.getSucursalId() == null) {
            return null;
        }
        return movimientoStockService.stockByProductoIdAndSucursalId(c.getProducto().getId(), c.getSucursalId());
    }
}
