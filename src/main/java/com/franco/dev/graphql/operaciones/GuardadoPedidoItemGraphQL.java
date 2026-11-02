package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.graphql.operaciones.input.PedidoItemDistribucionInput;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Alta o edición de un ítem del pedido con sus distribuciones por sucursal, en una sola transacción.
 *
 * Antes el desktop mandaba {@code savePedidoItem} y después {@code mergePedidoItemDistribuciones}: si la segunda
 * fallaba, el ítem quedaba guardado sin sus distribuciones (#488). Acá se llaman los mismos dos métodos con los mismos
 * datos, así que lo guardado es lo de siempre: solo cambia que es todo o nada.
 */
@Component
public class GuardadoPedidoItemGraphQL implements GraphQLMutationResolver {

    @Autowired
    private ComprasSecurityService seg;

    @Autowired
    private PedidoItemGraphQL pedidoItemGraphQL;
    @Autowired
    private PedidoItemDistribucionGraphQL pedidoItemDistribucionGraphQL;

    @Transactional
    public PedidoItem guardarPedidoItemConDistribuciones(PedidoItemInput pedidoItem,
                                                         List<PedidoItemDistribucionInput> distribuciones) {
        seg.exigirCompras();
        PedidoItem guardado = pedidoItemGraphQL.savePedidoItem(pedidoItem);
        List<PedidoItemDistribucionInput> conItem = new ArrayList<>();
        if (distribuciones != null) {
            for (PedidoItemDistribucionInput distribucion : distribuciones) {
                // El desktop las mandaba con el id del ítem recién guardado
                distribucion.setPedidoItemId(guardado.getId());
                conItem.add(distribucion);
            }
        }
        pedidoItemDistribucionGraphQL.mergePedidoItemDistribuciones(guardado.getId(), conItem);
        return guardado;
    }
}
