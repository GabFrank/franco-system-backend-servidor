package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class RecepcionMercaderiaItemService extends CrudService<RecepcionMercaderiaItem, RecepcionMercaderiaItemRepository, Long> {
    private final RecepcionMercaderiaItemRepository repository;

    @Override
    public RecepcionMercaderiaItemRepository getRepository() {
        return repository;
    }

    /**
     * Busca ítems por ID de recepción de mercadería
     */
    public List<RecepcionMercaderiaItem> findByRecepcionMercaderiaId(Long recepcionId) {
        return repository.findByRecepcionMercaderiaId(recepcionId);
    }

    /**
     * Busca ítems por ID de ítem de nota de recepción
     */
    public List<RecepcionMercaderiaItem> findByNotaRecepcionItemId(Long notaRecepcionItemId) {
        return repository.findByNotaRecepcionItemId(notaRecepcionItemId);
    }

    /**
     * Busca ítems por producto y sucursal de entrega
     */
    public List<RecepcionMercaderiaItem> findByProductoIdAndSucursalEntregaId(Long productoId, Long sucursalId) {
        return repository.findByProductoIdAndSucursalEntregaId(productoId, sucursalId);
    }

    /**
     * Busca ítems por lote y producto (para trazabilidad)
     */
    public List<RecepcionMercaderiaItem> findByLoteAndProductoId(String lote, Long productoId) {
        return repository.findByLoteAndProductoId(lote, productoId);
    }

    /**
     * Guarda un ítem de recepción
     */
    @Transactional
    public RecepcionMercaderiaItem save(RecepcionMercaderiaItem item) {
        return repository.save(item);
    }

    /**
     * Guarda un ítem de recepción con recepción existente
     */
    @Transactional
    public RecepcionMercaderiaItem saveWithRecepcion(RecepcionMercaderiaItem item, RecepcionMercaderia recepcion) {
        item.setRecepcionMercaderia(recepcion);
        return save(item);
    }

    /**
     * Obtiene la cantidad total recibida para un NotaRecepcionItem
     * @param notaRecepcionItemId ID del NotaRecepcionItem
     * @return Cantidad total recibida
     */
    public Double getCantidadRecibidaTotal(Long notaRecepcionItemId) {
        List<RecepcionMercaderiaItem> items = repository.findByNotaRecepcionItemId(notaRecepcionItemId);
        return items.stream()
                .mapToDouble(item -> item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0)
                .sum();
    }

    /**
     * Obtiene la cantidad total rechazada para un NotaRecepcionItem
     * @param notaRecepcionItemId ID del NotaRecepcionItem
     * @return Cantidad total rechazada
     */
    public Double getCantidadRechazadaTotal(Long notaRecepcionItemId) {
        List<RecepcionMercaderiaItem> items = repository.findByNotaRecepcionItemId(notaRecepcionItemId);
        return items.stream()
                .mapToDouble(item -> item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0)
                .sum();
    }

    /**
     * Determina el estado de recepción para un NotaRecepcionItem
     * @param notaRecepcionItemId ID del NotaRecepcionItem
     * @return Estado de recepción: PENDIENTE, VERIFICADO, RECHAZADO, PARCIAL
     */
    public String getEstadoRecepcion(Long notaRecepcionItemId) {
        List<RecepcionMercaderiaItem> items = repository.findByNotaRecepcionItemId(notaRecepcionItemId);
        
        if (items.isEmpty()) {
            return "PENDIENTE";
        }

        // Si hay algún ítem rechazado, el estado es RECHAZADO
        boolean tieneRechazados = items.stream()
                .anyMatch(item -> item.getCantidadRechazada() != null && item.getCantidadRechazada() > 0);
        
        if (tieneRechazados) {
            return "RECHAZADO";
        }

        // Si todos los ítems tienen cantidad recibida > 0, es VERIFICADO
        boolean todosVerificados = items.stream()
                .allMatch(item -> item.getCantidadRecibida() != null && item.getCantidadRecibida() > 0);
        
        if (todosVerificados) {
            return "VERIFICADO";
        }

        // Si algunos tienen cantidad recibida > 0, es PARCIAL
        boolean algunosVerificados = items.stream()
                .anyMatch(item -> item.getCantidadRecibida() != null && item.getCantidadRecibida() > 0);
        
        if (algunosVerificados) {
            return "PARCIAL";
        }

        return "PENDIENTE";
    }

    /**
     * Cancela la verificación de un ítem de recepción.
     * @param notaRecepcionItemId ID del NotaRecepcionItem
     * @param sucursalId ID de la sucursal
     * @return true si la verificación fue cancelada, false en caso contrario
     */
    @Transactional
    public Boolean cancelarVerificacion(Long notaRecepcionItemId, Long sucursalId) {
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("=== Iniciando cancelación de verificación ===");
        logger.info("NotaRecepcionItemId: {}, SucursalId: {}", notaRecepcionItemId, sucursalId);
        
        try {
            // 1. Buscar RecepcionMercaderiaItem por notaRecepcionItemId y sucursalId
            List<RecepcionMercaderiaItem> items = repository.findByNotaRecepcionItemIdAndSucursalId(notaRecepcionItemId, sucursalId);
            
            if (items.isEmpty()) {
                logger.warn("No se encontraron RecepcionMercaderiaItem para cancelar");
                return false;
            }
            
            // 2. Obtener la RecepcionMercaderia antes de eliminar los items
            RecepcionMercaderia recepcionMercaderia = null;
            for (RecepcionMercaderiaItem item : items) {
                recepcionMercaderia = item.getRecepcionMercaderia();
                break; // Todos los items tienen la misma recepción
            }
            
            // 3. Eliminar todos los RecepcionMercaderiaItem encontrados
            for (RecepcionMercaderiaItem item : items) {
                logger.info("Eliminando RecepcionMercaderiaItem ID: {}", item.getId());
                repository.delete(item);
            }
            
            // 4. Verificar si la RecepcionMercaderia queda vacía
            if (recepcionMercaderia != null) {
                Long cantidadItems = repository.countByRecepcionMercaderiaId(recepcionMercaderia.getId());
                logger.info("Cantidad de items restantes en recepción {}: {}", recepcionMercaderia.getId(), cantidadItems);
                
                if (cantidadItems == 0) {
                    logger.info("RecepcionMercaderia {} está vacía, será eliminada", recepcionMercaderia.getId());
                    // Aquí se eliminaría la RecepcionMercaderia si fuera necesario
                    // Por ahora solo logueamos
                }
            }
            
            logger.info("=== Cancelación de verificación completada exitosamente ===");
            return true;
            
        } catch (Exception e) {
            logger.error("=== ERROR durante cancelación de verificación ===");
            logger.error("Exception: {}", e.getMessage(), e);
            throw new RuntimeException("Error al cancelar verificación: " + e.getMessage());
        }
    }

    /**
     * Cancela el rechazo de un ítem de recepción.
     * @param notaRecepcionItemId ID del NotaRecepcionItem
     * @param sucursalId ID de la sucursal
     * @return true si el rechazo fue cancelado, false en caso contrario
     */
    @Transactional
    public Boolean cancelarRechazo(Long notaRecepcionItemId, Long sucursalId) {
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("=== Iniciando cancelación de rechazo ===");
        logger.info("NotaRecepcionItemId: {}, SucursalId: {}", notaRecepcionItemId, sucursalId);
        
        try {
            // 1. Buscar RecepcionMercaderiaItem rechazados por notaRecepcionItemId y sucursalId
            List<RecepcionMercaderiaItem> items = repository.findByNotaRecepcionItemIdAndSucursalIdAndRechazados(notaRecepcionItemId, sucursalId);
            
            if (items.isEmpty()) {
                logger.warn("No se encontraron RecepcionMercaderiaItem rechazados para cancelar");
                return false;
            }
            
            // 2. Obtener la RecepcionMercaderia antes de eliminar los items
            RecepcionMercaderia recepcionMercaderia = null;
            for (RecepcionMercaderiaItem item : items) {
                recepcionMercaderia = item.getRecepcionMercaderia();
                break; // Todos los items tienen la misma recepción
            }
            
            // 3. Eliminar todos los RecepcionMercaderiaItem rechazados encontrados
            for (RecepcionMercaderiaItem item : items) {
                logger.info("Eliminando RecepcionMercaderiaItem rechazado ID: {}", item.getId());
                repository.delete(item);
            }
            
            // 4. Verificar si la RecepcionMercaderia queda vacía
            if (recepcionMercaderia != null) {
                Long cantidadItems = repository.countByRecepcionMercaderiaId(recepcionMercaderia.getId());
                logger.info("Cantidad de items restantes en recepción {}: {}", recepcionMercaderia.getId(), cantidadItems);
                
                if (cantidadItems == 0) {
                    logger.info("RecepcionMercaderia {} está vacía, será eliminada", recepcionMercaderia.getId());
                    // Aquí se eliminaría la RecepcionMercaderia si fuera necesario
                    // Por ahora solo logueamos
                }
            }
            
            logger.info("=== Cancelación de rechazo completada exitosamente ===");
            return true;
            
        } catch (Exception e) {
            logger.error("=== ERROR durante cancelación de rechazo ===");
            logger.error("Exception: {}", e.getMessage(), e);
            throw new RuntimeException("Error al cancelar rechazo: " + e.getMessage());
        }
    }

    /**
     * Busca RecepcionMercaderiaItem por recepcionMercaderiaId y sucursales
     */
    public List<RecepcionMercaderiaItem> findByRecepcionMercaderiaIdAndSucursales(Long recepcionId, List<Long> sucursalesIds) {
        return repository.findByRecepcionMercaderiaIdAndSucursales(recepcionId, sucursalesIds);
    }
} 