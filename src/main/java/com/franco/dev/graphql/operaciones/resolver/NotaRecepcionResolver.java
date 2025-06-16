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

    public Integer cantidadItens(NotaRecepcion p){
        return pedidoItemService.countByNotaRecepcionId(p.getId());
    }

    public Double descuento(NotaRecepcion e){
        Double valor = 0.0;
        List<CompraItem> compraItemList = compraItemService.findByNotaRecepcionId(e.getId());
        for(CompraItem item: compraItemList){
            valor += item.getDescuentoUnitario() * item.getCantidad();
        }
        return valor;
    }

    public Integer cantidadItensVerificadoRecepcionMercaderia(NotaRecepcion p){
        return pedidoItemService.getRepository().countByNotaRecepcionIdAndVerificadoRecepcionProducto(p.getId(), true);
    }

    /**
     * Count items in this nota that need distribution (don't have complete sucursal distribution)
     * Considers the pedido estado to use the appropriate step fields for comparison
     */
    public Integer cantidadItensNecesitanDistribucion(NotaRecepcion p) {
        List<PedidoItem> itemsInNota = pedidoItemService.findByNotaRecepcionId(p.getId());
        int itemsNeedingDistribution = 0;
        
        for (PedidoItem item : itemsInNota) {
            // Skip cancelled items
            if (item.getCancelado() != null && item.getCancelado()) {
                continue;
            }
            
            // Get the pedido estado to determine which fields to use
            PedidoEstado pedidoEstado = item.getPedido().getEstado();
            
            // Get the appropriate cantidad and presentacion based on pedido estado
            Double expectedCantidad = getCantidadForEstado(item, pedidoEstado);
            Double expectedPresentacionCantidad = getCantidadPresentacionForEstado(item, pedidoEstado);
            
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
    }
    
    /**
     * Get cantidad based on pedido estado
     */
    private Double getCantidadForEstado(PedidoItem item, PedidoEstado estado) {
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getCantidadCreacion();
            case EN_RECEPCION_NOTA:
                return item.getCantidadRecepcionNota() != null ? 
                    item.getCantidadRecepcionNota() : item.getCantidadCreacion();
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getCantidadRecepcionProducto() != null ? 
                    item.getCantidadRecepcionProducto() : 
                    (item.getCantidadRecepcionNota() != null ? 
                        item.getCantidadRecepcionNota() : item.getCantidadCreacion());
            default:
                return item.getCantidadCreacion();
        }
    }

    /**
     * Get cantidad presentacion based on pedido estado
     */
    private Double getCantidadPresentacionForEstado(PedidoItem item, PedidoEstado estado) {
        switch (estado) {
            case ABIERTO:
            case ACTIVO:
                return item.getPresentacionCreacion() != null ? 
                    item.getPresentacionCreacion().getCantidad() : 1.0;
            case EN_RECEPCION_NOTA:
                return item.getPresentacionRecepcionNota() != null ? 
                    item.getPresentacionRecepcionNota().getCantidad() : 
                    (item.getPresentacionCreacion() != null ? 
                        item.getPresentacionCreacion().getCantidad() : 1.0);
            case EN_RECEPCION_MERCADERIA:
            case CONCLUIDO:
                return item.getPresentacionRecepcionProducto() != null ? 
                    item.getPresentacionRecepcionProducto().getCantidad() : 
                    (item.getPresentacionRecepcionNota() != null ? 
                        item.getPresentacionRecepcionNota().getCantidad() : 
                        (item.getPresentacionCreacion() != null ? 
                            item.getPresentacionCreacion().getCantidad() : 1.0));
            default:
                return item.getPresentacionCreacion() != null ? 
                    item.getPresentacionCreacion().getCantidad() : 1.0;
        }
    }

}
