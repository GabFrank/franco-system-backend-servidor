package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemDistribucion;
import com.franco.dev.domain.operaciones.PedidoSucursalEntrega;
import com.franco.dev.domain.operaciones.PedidoSucursalInfluencia;
import com.franco.dev.domain.operaciones.enums.CompraItemEstado;
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
    private PedidoItemDistribucionService pedidoItemDistribucionService;

    // ===== BASIC METHODS =====
    public List<PedidoItem> findByProductoId(Long id) { 
        return repository.findByProductoId(id); 
    }

    public Page<PedidoItem> findByPedidoId(Long id, Pageable page) { 
        return repository.findByPedidoIdOrderByIdDesc(id, page); 
    }

    public Page<PedidoItem> findByPedidoIdAndTexto(Long id, String texto, Pageable page) {
        return repository.findByPedidoIdAndProductoFilterOrderByIdDesc(id, texto, page);
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
        
        // **NEW LOGIC**: Create PedidoItemDistribucion automatically for new items
        if(isNewItem && entity.getPedido() != null) {
            createPedidoItemDistribucionForNewItem(e);
        }
        
        return e;
    }

    /**
     * Creates PedidoItemDistribucion records automatically for a new PedidoItem
     * based on the pedido's sucursales de influencia and entrega
     */
    private void createPedidoItemDistribucionForNewItem(PedidoItem pedidoItem) {
        try {
            Long pedidoId = pedidoItem.getPedido().getId();
            
            // Get sucursales de influencia (for which sucursals the pedido is being made)
            List<PedidoSucursalInfluencia> sucursalesInfluencia = 
                pedidoSucursalInfluenciaService.findByPedidoId(pedidoId);
                
            // Get sucursales de entrega (where the pedido will be delivered by the provider)
            List<PedidoSucursalEntrega> sucursalesEntrega = 
                pedidoSucursalEntregaService.findByPedidoId(pedidoId);
            
            // Calculate cantidadAsignada based on business rules using basic fields
            Double cantidadAsignada = 0.0;
            
            // Using basic PedidoItem fields instead of step-specific ones
            if(sucursalesInfluencia.size() == 1 && sucursalesEntrega.size() == 1) {
                // If there's exactly one sucursal de influencia and one sucursal de entrega
                // cantidadAsignada = cantidadSolicitada (ya está en unidades base)
                // cantidadSolicitada is already in base units (calculated in frontend as cantidadPorPresentacion * presentacion.cantidad)
                Double cantidadSolicitada = pedidoItem.getCantidadSolicitada() != null 
                    ? pedidoItem.getCantidadSolicitada() : 0.0;
                cantidadAsignada = cantidadSolicitada;
            }
            // Otherwise cantidadAsignada remains 0.0 (for manual distribution)
            
            // Create PedidoItemDistribucion for each combination of sucursal influencia and sucursal entrega
            for(PedidoSucursalInfluencia sucursalInfluencia : sucursalesInfluencia) {
                for(PedidoSucursalEntrega sucursalEntrega : sucursalesEntrega) {
                    PedidoItemDistribucion pedidoItemDistribucion = new PedidoItemDistribucion();
                    pedidoItemDistribucion.setPedidoItem(pedidoItem);
                    pedidoItemDistribucion.setSucursalInfluencia(sucursalInfluencia.getSucursal());
                    pedidoItemDistribucion.setSucursalEntrega(sucursalEntrega.getSucursal());
                    pedidoItemDistribucion.setCantidadAsignada(cantidadAsignada);
                    
                    // Save the PedidoItemDistribucion
                    pedidoItemDistribucionService.save(pedidoItemDistribucion);
                }
            }
            
        } catch (Exception ex) {
            // Log the error but don't break the main PedidoItem saving process
            System.err.println("Error creating PedidoItemDistribucion for PedidoItem " + pedidoItem.getId() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Calcula la cantidad pendiente de conciliar para un PedidoItem
     * @param pedidoItemId ID del pedido item
     * @return Cantidad pendiente (cantidadSolicitada - sum(cantidadEnNota))
     */
    public Double getCantidadPendiente(Long pedidoItemId) {
        if (pedidoItemId == null) {
            return 0.0;
        }
        
        Double cantidadPendiente = repository.getCantidadPendienteByPedidoItemId(pedidoItemId);
        return cantidadPendiente != null ? Math.max(0.0, cantidadPendiente) : 0.0;
    }
}