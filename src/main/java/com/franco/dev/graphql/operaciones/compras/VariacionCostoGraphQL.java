package com.franco.dev.graphql.operaciones.compras;

import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.service.operaciones.compras.VariacionCostoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ítems del pedido: precio contra la última compra y vencimiento desde la tabla.
 *
 * Usado en: Desktop sí (gestión de compras, «Ítems del pedido») · Mobile no.
 */
@Component
public class VariacionCostoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private VariacionCostoService service;

    public List<VariacionCostoPedidoItem> variacionCostoPedidoItems(Long pedidoId) {
        return service.variaciones(pedidoId);
    }

    public PedidoItem actualizarVencimientoPedidoItem(Long pedidoItemId, String vencimiento) {
        return service.actualizarVencimiento(pedidoItemId, vencimiento);
    }
}
