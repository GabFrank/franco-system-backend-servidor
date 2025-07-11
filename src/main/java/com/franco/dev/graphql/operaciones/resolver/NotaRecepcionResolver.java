package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotaRecepcionResolver implements GraphQLResolver<NotaRecepcion> {

    @Autowired
    private NotaRecepcionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private CompraItemService compraItemService;

    public Double valor(NotaRecepcion e){
        Double res = service.getRepository().valor(e.getId());
        if(res != null){
            return res;
        } else {
            return 0.0;
        }
    }

    // public Integer cantidadItens(NotaRecepcion p){
    //     return pedidoItemService.countByNotaRecepcionId(p.getId());
    // }

    public Double descuento(NotaRecepcion e){
        Double valor = 0.0;
        List<CompraItem> compraItemList = compraItemService.findByNotaRecepcionId(e.getId());
        for(CompraItem item: compraItemList){
            valor += item.getDescuentoUnitario() * item.getCantidad();
        }
        return valor;
    }

    /**
     * Count verified items for a NotaRecepcion
     * TODO: Will be reimplemented when RecepcionMercaderiaItem entity is properly integrated
     * For now, return 0 as verificado field is in step-specific entities
     */
    public Integer cantidadItensVerificadoRecepcionMercaderia(NotaRecepcion p){
        // TODO: Implement using RecepcionMercaderiaItem queries
        return 0;
    }



    /**
     * Count items in this nota that need distribution (don't have complete sucursal distribution)
     * TODO: Needs reimplementation with new entity structure
     * - PedidoItem no longer has step-specific fields (cancelado, cantidadCreacion, etc.)
     * - Use NotaRecepcionItem for nota reception step data
     * - Use RecepcionMercaderiaItem for physical reception step data
     * - Use basic PedidoItem fields (cantidadSolicitada) as fallback
     */
    public Integer cantidadItensNecesitanDistribucion(NotaRecepcion p) {
        // TODO: Reimplement using new entity structure
        // For now, return 0 to avoid compilation errors
        return 0;
        
        /*
        // COMMENTED OUT - Original logic used deleted fields
        List<PedidoItem> itemsInNota = pedidoItemService.findByNotaRecepcionId(p.getId());
        int itemsNeedingDistribution = 0;
        
        for (PedidoItem item : itemsInNota) {
            // Skip cancelled items - CANCELLED FIELD DELETED
            // TODO: Check if NotaRecepcionItem has cancelado field or use different approach
            
            // Get the pedido estado to determine which fields to use
            PedidoEstado pedidoEstado = item.getPedido().getEstado();
            
            // Get the appropriate cantidad and presentacion based on pedido estado
            // TODO: Use NotaRecepcionItem/RecepcionMercaderiaItem for step-specific data
            Double expectedCantidad = item.getCantidadSolicitada(); // Use basic field as fallback
            Double expectedPresentacionCantidad = item.getPresentacion() != null ? 
                item.getPresentacion().getCantidad() : 1.0; // Use basic presentacion
            
            if (expectedCantidad != null && expectedCantidad > 0 && expectedPresentacionCantidad != null) {
                // Calculate total expected quantity in units
                double totalExpectedQuantity = expectedCantidad * expectedPresentacionCantidad;
                
                // Get actual distributed quantity from PedidoItemSucursal
                Double distributedQuantity = pedidoItemService.getRepository()
                    .getTotalDistributedQuantityByPedidoItemId(item.getId());
                
                if (distributedQuantity == null) {
                    distributedQuantity = 0.0;
                }
                
                // Item needs distribution if distributed quantity is not equal to expected
                if (!distributedQuantity.equals(totalExpectedQuantity)) {
                    itemsNeedingDistribution++;
                }
            }
        }
        
        return itemsNeedingDistribution;
        */
    }
    
    /**
     * Get cantidad based on pedido estado
     * TODO: Needs reimplementation with new entity structure
     * - Step-specific cantidad fields deleted from PedidoItem
     * - Use NotaRecepcionItem for EN_RECEPCION_NOTA state
     * - Use RecepcionMercaderiaItem for EN_RECEPCION_MERCADERIA/CONCLUIDO states
     * - Use cantidadSolicitada as fallback
     */
    private Double getCantidadForEstado(PedidoItem item, PedidoEstado estado) {
        // TODO: Reimplement using new entity structure
        // For now, always return cantidadSolicitada
        return item.getCantidadSolicitada();
        
        /*
        // COMMENTED OUT - Original logic used deleted fields
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getCantidadCreacion(); // DELETED FIELD
            case EN_RECEPCION_NOTA:
                return item.getCantidadRecepcionNota() != null ? // DELETED FIELD
                    item.getCantidadRecepcionNota() : item.getCantidadCreacion(); // DELETED FIELDS
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getCantidadRecepcionProducto() != null ? // DELETED FIELD
                    item.getCantidadRecepcionProducto() : // DELETED FIELD
                    (item.getCantidadRecepcionNota() != null ? // DELETED FIELD
                        item.getCantidadRecepcionNota() : item.getCantidadCreacion()); // DELETED FIELDS
            default:
                return item.getCantidadCreacion(); // DELETED FIELD
        }
        */
    }

    /**
     * Get cantidad presentacion based on pedido estado
     * TODO: Needs reimplementation with new entity structure
     * - Step-specific presentacion fields deleted from PedidoItem
     * - Use NotaRecepcionItem for EN_RECEPCION_NOTA state
     * - Use RecepcionMercaderiaItem for EN_RECEPCION_MERCADERIA/CONCLUIDO states
     * - Use basic presentacion as fallback
     */
    private Double getCantidadPresentacionForEstado(PedidoItem item, PedidoEstado estado) {
        // TODO: Reimplement using new entity structure
        // For now, always return basic presentacion cantidad
        return item.getPresentacionCreacion() != null ? item.getPresentacionCreacion().getCantidad() : 1.0;
        
        /*
        // COMMENTED OUT - Original logic used deleted fields
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getPresentacionCreacion() != null ? // DELETED FIELD
                    item.getPresentacionCreacion().getCantidad() : 1.0;
            case EN_RECEPCION_NOTA:
                return item.getPresentacionRecepcionNota() != null ? // DELETED FIELD
                    item.getPresentacionRecepcionNota().getCantidad() : // DELETED FIELD
                    (item.getPresentacionCreacion() != null ? // DELETED FIELD
                        item.getPresentacionCreacion().getCantidad() : 1.0);
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getPresentacionRecepcionProducto() != null ? // DELETED FIELD
                    item.getPresentacionRecepcionProducto().getCantidad() : // DELETED FIELD
                    (item.getPresentacionRecepcionNota() != null ? // DELETED FIELD
                        item.getPresentacionRecepcionNota().getCantidad() : // DELETED FIELD
                        (item.getPresentacionCreacion() != null ? // DELETED FIELD
                            item.getPresentacionCreacion().getCantidad() : 1.0));
            default:
                return item.getPresentacionCreacion() != null ? // DELETED FIELD
                    item.getPresentacionCreacion().getCantidad() : 1.0;
        }
        */
    }

}
