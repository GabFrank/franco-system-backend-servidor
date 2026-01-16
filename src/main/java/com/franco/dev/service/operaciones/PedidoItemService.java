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
import java.util.Optional;

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

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

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
        
        // Obtener el item original antes de guardar (para comparación en actualizaciones)
        PedidoItem originalItem = null;
        Double cantidadSolicitadaOriginal = null;
        if (!isNewItem && entity.getId() != null) {
            Optional<PedidoItem> originalOpt = repository.findById(entity.getId());
            if (originalOpt.isPresent()) {
                originalItem = originalOpt.get();
                cantidadSolicitadaOriginal = originalItem.getCantidadSolicitada();
            }
        }
        
        if(isNewItem) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        
        e = super.save(entity);
        
        // **NEW LOGIC**: Create PedidoItemDistribucion automatically for new items
        if(isNewItem && entity.getPedido() != null) {
            createPedidoItemDistribucionForNewItem(e);
        }
        
        // **NEW LOGIC**: Update PedidoItemDistribucion automatically for existing items in safe cases
        if (!isNewItem && entity.getPedido() != null && cantidadSolicitadaOriginal != null) {
            updatePedidoItemDistribucionForExistingItem(e, cantidadSolicitadaOriginal);
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
     * Actualiza automáticamente las distribuciones de un PedidoItem existente
     * Solo en el caso seguro: 1 sucursal de influencia, 1 sucursal de entrega,
     * distribución nunca editada manualmente, y sin NotaRecepcionItem asociados
     * 
     * @param pedidoItem PedidoItem actualizado
     * @param cantidadSolicitadaOriginal Cantidad solicitada original antes de la actualización
     */
    private void updatePedidoItemDistribucionForExistingItem(PedidoItem pedidoItem, Double cantidadSolicitadaOriginal) {
        try {
            Long pedidoId = pedidoItem.getPedido().getId();
            Long pedidoItemId = pedidoItem.getId();
            Double nuevaCantidadSolicitada = pedidoItem.getCantidadSolicitada();
            
            // Validaciones básicas
            if (pedidoId == null || pedidoItemId == null || nuevaCantidadSolicitada == null || cantidadSolicitadaOriginal == null) {
                return;
            }
            
            // Si la cantidad no cambió, no hay nada que actualizar
            if (Math.abs(nuevaCantidadSolicitada - cantidadSolicitadaOriginal) < 0.001) {
                return;
            }
            
            // Verificar que no haya NotaRecepcionItem asociados
            // Si hay NotaRecepcionItem, no actualizar automáticamente porque pueden depender de la distribución actual
            List<com.franco.dev.domain.operaciones.NotaRecepcionItem> notaRecepcionItems = 
                notaRecepcionItemService.findByPedidoItemId(pedidoItemId);
            if (notaRecepcionItems != null && !notaRecepcionItems.isEmpty()) {
                return; // Hay NotaRecepcionItem asociados, no actualizar automáticamente
            }
            
            // Get sucursales de influencia y entrega
            List<PedidoSucursalInfluencia> sucursalesInfluencia = 
                pedidoSucursalInfluenciaService.findByPedidoId(pedidoId);
            List<PedidoSucursalEntrega> sucursalesEntrega = 
                pedidoSucursalEntregaService.findByPedidoId(pedidoId);
            
            // Solo actualizar automáticamente si hay exactamente 1 sucursal de influencia y 1 sucursal de entrega
            if (sucursalesInfluencia.size() != 1 || sucursalesEntrega.size() != 1) {
                return; // Caso múltiple, requiere distribución manual
            }
            
            // Obtener las distribuciones existentes
            List<PedidoItemDistribucion> distribucionesExistentes = 
                pedidoItemDistribucionService.findByPedidoItemId(pedidoItemId);
            
            // Debe haber exactamente 1 distribución (1 sucursal influencia x 1 sucursal entrega = 1 combinación)
            if (distribucionesExistentes == null || distribucionesExistentes.size() != 1) {
                return; // No hay distribución o hay múltiples, no actualizar automáticamente
            }
            
            PedidoItemDistribucion distribucion = distribucionesExistentes.get(0);
            Double cantidadAsignadaActual = distribucion.getCantidadAsignada();
            
            // Verificar que la distribución nunca fue editada manualmente
            // Si cantidadAsignadaActual == cantidadSolicitadaOriginal, significa que sigue siendo automática
            // Usamos una tolerancia pequeña para manejar errores de punto flotante
            double tolerancia = 0.001;
            boolean distribucionEsAutomatica = cantidadAsignadaActual != null && 
                Math.abs(cantidadAsignadaActual - cantidadSolicitadaOriginal) <= tolerancia;
            
            if (!distribucionEsAutomatica) {
                return; // La distribución fue editada manualmente, no actualizar automáticamente
            }
            
            // Caso seguro: actualizar proporcionalmente la cantidad asignada
            // Como solo hay 1 distribución y 1-1 sucursales, simplemente actualizamos a la nueva cantidad
            distribucion.setCantidadAsignada(nuevaCantidadSolicitada);
            pedidoItemDistribucionService.save(distribucion);
            
        } catch (Exception ex) {
            // Log the error but don't break the main PedidoItem saving process
            System.err.println("Error updating PedidoItemDistribucion for PedidoItem " + pedidoItem.getId() + ": " + ex.getMessage());
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