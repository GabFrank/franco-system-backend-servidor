package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemSucursal;
import com.franco.dev.domain.operaciones.PedidoSucursalEntrega;
import com.franco.dev.domain.operaciones.PedidoSucursalInfluencia;
import com.franco.dev.domain.operaciones.enums.CompraItemEstado;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.repository.operaciones.PedidoItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class PedidoItemService extends CrudService<PedidoItem, PedidoItemRepository, Long> {
    private final PedidoItemRepository repository;

    @Override
    public PedidoItemRepository getRepository() {
        return repository;
    }

    @Autowired
    public CompraItemService compraItemService;

    @Autowired
    private PedidoSucursalInfluenciaService pedidoSucursalInfluenciaService;

    @Autowired
    private PedidoSucursalEntregaService pedidoSucursalEntregaService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

    // ===== BASIC METHODS =====
    public List<PedidoItem> findByProductoId(Long id) { 
        return repository.findByProductoId(id); 
    }

    public Page<PedidoItem> findByPedidoId(Long id, Pageable page) { 
        return repository.findByPedidoIdOrderByIdDesc(id, page); 
    }

    public Page<PedidoItem> findByPedidoIdAndTexto(Long id, String texto, Pageable page) {
        return repository.findByPedidoIdAndProductoDescripcionLikeOrderByIdDesc(id, texto, page);
    }

    public List<PedidoItem> findByPedidoId(Long id) { 
        return repository.findByPedidoId(id); 
    }

    @Override
    public PedidoItem save(PedidoItem entity) {
        PedidoItem e = null;
        boolean isNewItem = entity.getId() == null;
        
        if(isNewItem) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        
        e = super.save(entity);
        
        // **NEW LOGIC**: Create PedidoItemSucursal automatically for new items
        if(isNewItem && entity.getPedido() != null) {
            createPedidoItemSucursalForNewItem(e);
        }
        
        return e;
    }

    /**
     * Creates PedidoItemSucursal records automatically for a new PedidoItem
     * based on the pedido's sucursales de influencia and entrega
     */
    private void createPedidoItemSucursalForNewItem(PedidoItem pedidoItem) {
        try {
            Long pedidoId = pedidoItem.getPedido().getId();
            
            // Get sucursales de influencia (for which sucursals the pedido is being made)
            List<PedidoSucursalInfluencia> sucursalesInfluencia = 
                pedidoSucursalInfluenciaService.findByPedidoId(pedidoId);
                
            // Get sucursales de entrega (where the pedido will be delivered by the provider)
            List<PedidoSucursalEntrega> sucursalesEntrega = 
                pedidoSucursalEntregaService.findByPedidoId(pedidoId);
            
            // Calculate cantidadPorUnidad based on business rules using basic fields
            Double cantidadPorUnidad = 0.0;
            
            // Using basic PedidoItem fields instead of step-specific ones
            if(sucursalesInfluencia.size() == 1 && sucursalesEntrega.size() == 1) {
                // If there's exactly one sucursal de influencia and one sucursal de entrega
                // cantidadPorUnidad = presentacionCreacion.cantidad * cantidadSolicitada
                Double presentacionCantidad = pedidoItem.getPresentacionCreacion() != null 
                    ? pedidoItem.getPresentacionCreacion().getCantidad() : 1.0;
                Double cantidadSolicitada = pedidoItem.getCantidadSolicitada() != null 
                    ? pedidoItem.getCantidadSolicitada() : 0.0;
                cantidadPorUnidad = presentacionCantidad * cantidadSolicitada;
            }
            // Otherwise cantidadPorUnidad remains 0.0 (for manual distribution)
            
            // Determine sucursalEntrega for the PedidoItemSucursal records
            Long sucursalEntregaId = null;
            if(sucursalesEntrega.size() == 1) {
                // If there's only one sucursal de entrega, assign it
                sucursalEntregaId = sucursalesEntrega.get(0).getSucursal().getId();
            }
            // If there are multiple sucursales de entrega, leave sucursal_entrega_id null
            
            // Create one PedidoItemSucursal for each sucursal de influencia
            for(PedidoSucursalInfluencia sucursalInfluencia : sucursalesInfluencia) {
                PedidoItemSucursal pedidoItemSucursal = new PedidoItemSucursal();
                pedidoItemSucursal.setPedidoItem(pedidoItem);
                pedidoItemSucursal.setSucursal(sucursalInfluencia.getSucursal());
                pedidoItemSucursal.setCantidadPorUnidad(cantidadPorUnidad);
                pedidoItemSucursal.setUsuario(pedidoItem.getUsuarioCreacion());
                
                // Set sucursalEntrega if there's only one
                if(sucursalEntregaId != null) {
                    // Find the sucursal entity
                    for(PedidoSucursalEntrega entrega : sucursalesEntrega) {
                        if(entrega.getSucursal().getId().equals(sucursalEntregaId)) {
                            pedidoItemSucursal.setSucursalEntrega(entrega.getSucursal());
                            break;
                        }
                    }
                }
                
                // Save the PedidoItemSucursal
                pedidoItemSucursalService.save(pedidoItemSucursal);
            }
            
        } catch (Exception ex) {
            // Log the error but don't break the main PedidoItem saving process
            System.err.println("Error creating PedidoItemSucursal for PedidoItem " + pedidoItem.getId() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}