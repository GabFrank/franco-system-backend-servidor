package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.PedidoSucursalEntregaService;
import com.franco.dev.service.operaciones.PedidoSucursalInfluenciaService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;

import graphql.kickstart.tools.GraphQLResolver;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PedidoResolver implements GraphQLResolver<Pedido> {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ProcesoEtapaService procesoEtapaService;

    // Sucursa entrega and influencia service
    @Autowired
    private PedidoSucursalEntregaService pedidoSucursalEntregaService;

    @Autowired
    private PedidoSucursalInfluenciaService pedidoSucursalInfluenciaService;

    public List<PedidoSucursalEntrega> getSucursalEntregaList(Pedido pedido) {
        return pedidoSucursalEntregaService.findByPedidoId(pedido.getId());
    }

    public List<PedidoSucursalInfluencia> getSucursalInfluenciaList(Pedido pedido) {
        return pedidoSucursalInfluenciaService.findByPedidoId(pedido.getId());
    }

    public List<ProcesoEtapa> getProcesoEtapas(Pedido pedido) {
        return procesoEtapaService.getEtapasByPedidoId(pedido.getId());
    }

    public Double getMontoTotal(Pedido pedido) {
        return pedidoService.getMontoTotal(pedido.getId());
    }
    
    // All resolver methods removed since GraphQL schema now only includes entity fields
    // No computed fields = no resolvers needed
    
}
