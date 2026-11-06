package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.operaciones.DocumentoRecibidoItemRepository;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los renglones de la factura que quedaron afuera al crear la compra con solo los reconocidos (#124): el desktop los
 * carga como ítems del pedido y, si se borra el ítem, el renglón vuelve a quedar pendiente.
 */
@Service
public class RenglonPendienteService {

    static final String YA_CARGADO = "Ese renglón de la factura ya está cargado.";
    static final String DE_OTRO_PEDIDO = "Ese renglón no es de una factura de este pedido.";
    static final String DESCARTADO = "Ese renglón de la factura está descartado: restauralo para cargarlo.";

    @Autowired private DocumentoRecibidoItemRepository renglonRepository;
    @Autowired private AprendizajeProveedor aprendizaje;

    /**
     * Vincula un renglón pendiente (sin producto, no descartado) con el ítem recién guardado, como «Vincular» +
     * «Recordar» del celular: producto y presentación del ítem, factor = unidades de la presentación y lo aprendido
     * para las próximas facturas del proveedor.
     */
    @Transactional
    public void vincularAlItem(Long renglonId, PedidoItem item) {
        DocumentoRecibidoItem renglon = renglonId != null ? renglonRepository.findById(renglonId).orElse(null) : null;
        if (renglon == null) {
            throw new GraphQLException("No se encontró el renglón de la factura.");
        }
        if (renglon.getProducto() != null) {
            throw new GraphQLException(YA_CARGADO);
        }
        if (Boolean.TRUE.equals(renglon.getDescartado())) {
            throw new GraphQLException(DESCARTADO);
        }
        DocumentoRecibido doc = renglon.getDocumentoRecibido();
        if (doc == null || doc.getPedido() == null || item.getPedido() == null
                || !doc.getPedido().getId().equals(item.getPedido().getId())
                || !Boolean.TRUE.equals(doc.getCargarAlFinalizar()) || doc.getNotaRecepcion() != null) {
            throw new GraphQLException(DE_OTRO_PEDIDO);
        }
        if (item.getProducto() == null) {
            throw new GraphQLException("Elegí el producto del ítem.");
        }
        Presentacion presentacion = item.getPresentacionCreacion();
        double factor = presentacion != null && presentacion.getCantidad() != null && presentacion.getCantidad() > 0
                ? presentacion.getCantidad() : 1.0;
        renglon.setProducto(item.getProducto());
        renglon.setPresentacion(presentacion);
        renglon.setFactor(factor);
        renglon.setTipoMatch(ComprasMobileService.TipoMatch.MANUAL.name());
        renglon.setPedidoItem(item);
        renglonRepository.save(renglon);
        if (presentacion != null && doc.getProveedor() != null && AprendizajeProveedor.sePuedeAprender(renglon)) {
            aprendizaje.aprender(doc.getProveedor(), renglon, presentacion, factor);
        }
    }

    /**
     * Antes de borrar un ítem (o de cambiarle el producto): sus renglones de facturas sin nota vuelven a pendiente.
     * Va antes del borrado porque la FK {@code pedido_item_id} es ON DELETE SET NULL y después ya no se los encuentra.
     */
    @Transactional
    public void liberarDelItem(Long pedidoItemId) {
        if (pedidoItemId == null) return;
        for (DocumentoRecibidoItem renglon : renglonRepository.findByPedidoItemId(pedidoItemId)) {
            DocumentoRecibido doc = renglon.getDocumentoRecibido();
            if (doc == null || doc.getNotaRecepcion() != null) continue;
            renglon.setProducto(null);
            renglon.setPresentacion(null);
            renglon.setFactor(null);
            renglon.setTipoMatch(null);
            renglon.setPedidoItem(null);
            renglon.setObservacion(null);
            renglonRepository.save(renglon);
        }
    }
}
