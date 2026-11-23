package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.graphql.operaciones.input.PedidoItemDistribucionInput;
import com.franco.dev.graphql.operaciones.input.PedidoItemInput;
import com.franco.dev.service.operaciones.compras.RenglonPendienteService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Alta o edición de un ítem del pedido con sus distribuciones por sucursal, en una sola transacción.
 *
 * Antes el desktop mandaba {@code savePedidoItem} y después {@code mergePedidoItemDistribuciones}: si la segunda
 * fallaba, el ítem quedaba guardado sin sus distribuciones (#488). Acá se llaman los mismos dos métodos con los mismos
 * datos, así que lo guardado es lo de siempre: solo cambia que es todo o nada.
 *
 * Con {@code renglonFacturaId}, el renglón pendiente de una factura del pedido queda cargado con el ítem (#124); si al
 * editar se cambia el producto, los renglones que tenía vuelven a pendiente.
 */
@Component
public class GuardadoPedidoItemGraphQL implements GraphQLMutationResolver {

    @Autowired
    private ComprasSecurityService seg;

    @Autowired
    private PedidoItemGraphQL pedidoItemGraphQL;
    @Autowired
    private PedidoItemDistribucionGraphQL pedidoItemDistribucionGraphQL;
    @Autowired
    private RenglonPendienteService renglonPendienteService;

    @Transactional
    public PedidoItem guardarPedidoItemConDistribuciones(PedidoItemInput pedidoItem,
                                                         List<PedidoItemDistribucionInput> distribuciones,
                                                         Long renglonFacturaId) {
        seg.exigirCompras();
        if (pedidoItem.getId() != null) {
            PedidoItem actual = pedidoItemGraphQL.pedidoItem(pedidoItem.getId()).orElse(null);
            Long productoActual = actual != null && actual.getProducto() != null ? actual.getProducto().getId() : null;
            if (actual != null && !Objects.equals(productoActual, pedidoItem.getProductoId())) {
                // Los renglones de la factura eran del producto anterior
                renglonPendienteService.liberarDelItem(pedidoItem.getId());
            }
        }
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
        if (renglonFacturaId != null) {
            renglonPendienteService.vincularAlItem(renglonFacturaId, guardado);
        }
        return guardado;
    }
}
