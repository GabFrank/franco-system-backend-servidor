package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.CosteoMoneda;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CostoPorProductoResolver implements GraphQLResolver<CostoPorProducto> {

    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Autowired
    private CosteoMoneda costeoMoneda;

//    public List<PrecioPorSucursal> precioPorSucursalPorProductoId(PrecioPorSucursal c){
//
//        return precioPorSucursalService.find(c.getId());
//    }

    /** La guardada en la fila; en una fila anterior a V259.5, la de costeo de ahora. */
    public Moneda monedaCosteo(CostoPorProducto c) {
        return c.getMonedaCosteo() != null ? c.getMonedaCosteo() : costeoMoneda.monedaDeCosteo();
    }

    public Double costoMedioCosteo(CostoPorProducto c) {
        return valor(costeoMoneda.puntoDePartida(c, monedaCosteo(c)).orElse(null));
    }

    public Double ultimoPrecioCompraCosteo(CostoPorProducto c) {
        return valor(costeoMoneda.ultimoPrecioDePartida(c, monedaCosteo(c)).orElse(null));
    }

    private static Double valor(BigDecimal v) {
        return v != null ? v.doubleValue() : null;
    }
}
