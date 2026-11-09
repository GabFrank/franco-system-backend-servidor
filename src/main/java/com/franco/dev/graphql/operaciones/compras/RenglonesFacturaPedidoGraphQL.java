package com.franco.dev.graphql.operaciones.compras;

import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
import com.franco.dev.service.operaciones.compras.RenglonPendienteService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Los renglones de la factura que quedaron afuera de la compra creada con solo los reconocidos (#124): la lista para
 * la planificación y descartar / restaurar uno con su motivo.
 *
 * Usado en: Desktop sí (planificación del pedido) · Mobile no.
 */
@Component
public class RenglonesFacturaPedidoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired private ComprasSecurityService seg;
    @Autowired private RenglonPendienteService service;

    public List<FacturaPendientePedido> renglonesFacturaPendientesPedido(Long pedidoId) {
        seg.exigirCompras();
        return service.pendientesDelPedido(pedidoId);
    }

    public Boolean descartarRenglonFactura(Long renglonId, String motivo) {
        seg.exigirCompras();
        service.descartar(renglonId, motivo, seg.usuarioActual());
        return true;
    }

    public Boolean restaurarRenglonFactura(Long renglonId) {
        seg.exigirCompras();
        service.restaurar(renglonId);
        return true;
    }
}
