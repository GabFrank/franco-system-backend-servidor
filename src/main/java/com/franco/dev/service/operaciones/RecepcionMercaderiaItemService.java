package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemRepository;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemVariacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.graphql.operaciones.dto.RecepcionSumarioDTO;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;

import java.math.BigDecimal;

@Service
@AllArgsConstructor
public class RecepcionMercaderiaItemService extends CrudService<RecepcionMercaderiaItem, RecepcionMercaderiaItemRepository, Long> {
    private final RecepcionMercaderiaItemRepository repository;
    private final RecepcionMercaderiaItemVariacionRepository recepcionMercaderiaItemVariacionRepository;
    private final EntityManager entityManager;

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
        return calcularEstadoRecepcion(items);
    }

    /**
     * Obtiene el estado de recepción filtrando por sucursales específicas
     * @param notaRecepcionItemId ID del NotaRecepcionItem
     * @param sucursalesIds Lista de IDs de sucursales para filtrar
     * @return Estado de recepción física: PENDIENTE, VERIFICADO, RECHAZADO, PARCIAL
     */
    public String getEstadoRecepcion(Long notaRecepcionItemId, List<Long> sucursalesIds) {
        if (sucursalesIds == null || sucursalesIds.isEmpty()) {
            // Si no se proporcionan sucursales, usar el método sin filtro
            return getEstadoRecepcion(notaRecepcionItemId);
        }
        
        List<RecepcionMercaderiaItem> items = repository.findByNotaRecepcionItemIdAndSucursalesIds(notaRecepcionItemId, sucursalesIds);
        return calcularEstadoRecepcion(items);
    }

    /**
     * Calcula el estado de recepción basado en una lista de RecepcionMercaderiaItem
     * @param items Lista de items de recepción de mercadería
     * @return Estado de recepción física: PENDIENTE, VERIFICADO, RECHAZADO, PARCIAL
     */
    private String calcularEstadoRecepcion(List<RecepcionMercaderiaItem> items) {
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
     * Resetea la verificación de un ítem de recepción (elimina variaciones y resetea estado)
     * @param recepcionMercaderiaItemId ID del RecepcionMercaderiaItem
     * @return true si la verificación fue reseteada, false en caso contrario
     */
    @Transactional
    public Boolean resetearVerificacion(Long recepcionMercaderiaItemId) {
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("=== Iniciando reseteo de verificación ===");
        logger.info("RecepcionMercaderiaItemId: {}", recepcionMercaderiaItemId);
        
        try {
            // 1. Buscar RecepcionMercaderiaItem por su ID
            RecepcionMercaderiaItem item = repository.findById(recepcionMercaderiaItemId)
                .orElse(null);
            
            if (item == null) {
                logger.warn("No se encontró RecepcionMercaderiaItem con ID: {}", recepcionMercaderiaItemId);
                return false;
            }
            
            logger.info("Reseteando RecepcionMercaderiaItem ID: {} - Producto: {}", 
                item.getId(), item.getProducto() != null ? item.getProducto().getDescripcion() : "N/A");
            
            // 2. Buscar y eliminar todas las variaciones del item
            List<RecepcionMercaderiaItemVariacion> variaciones = recepcionMercaderiaItemVariacionRepository.findByRecepcionMercaderiaItemId(item.getId());
            
            if (variaciones != null && !variaciones.isEmpty()) {
                logger.info("Eliminando {} variaciones del item {}", variaciones.size(), item.getId());
                for (RecepcionMercaderiaItemVariacion variacion : variaciones) {
                    recepcionMercaderiaItemVariacionRepository.delete(variacion);
                    logger.info("Variación {} eliminada", variacion.getId());
                }
            } else {
                logger.info("No se encontraron variaciones para el item {}", item.getId());
            }
            
            // 3. Resetear cantidades y estado
            item.setCantidadRecibida(0.0);
            item.setCantidadRechazada(0.0);
            item.setEstadoVerificacion(EstadoVerificacion.PENDIENTE);
            
            // 4. Guardar el item reseteado
            repository.save(item);
            logger.info("Item {} reseteado exitosamente", item.getId());
            
            logger.info("=== Reseteo de verificación completado exitosamente ===");
            return true;
            
        } catch (Exception e) {
            logger.error("=== ERROR durante reseteo de verificación ===");
            logger.error("Exception: {}", e.getMessage(), e);
            throw new RuntimeException("Error al resetear verificación: " + e.getMessage());
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

    /**
     * Obtiene ítems por ID de recepción de mercadería con paginación y filtros
     * @param recepcionId ID de la recepción de mercadería
     * @param filtroTexto Texto para filtrar por nombre de producto o código
     * @param estados Lista de estados de verificación para filtrar (opcional)
     * @param pageable Parámetros de paginación
     * @return Página de ítems de recepción
     */
    public Page<RecepcionMercaderiaItem> findByRecepcionMercaderiaIdPaginados(
            Long recepcionId, String filtroTexto, List<EstadoVerificacion> estados, Pageable pageable) {
        
        if (recepcionId == null) {
            throw new IllegalArgumentException("ID de recepción es requerido");
        }

        // Usar QueryBuilder para filtrar por array de estados
        return findByRecepcionMercaderiaIdPaginadosConEstados(recepcionId, filtroTexto, estados, pageable);
    }

    /**
     * Obtiene ítems por ID de recepción de mercadería con paginación, filtros y array de estados usando QueryBuilder
     * @param recepcionId ID de la recepción de mercadería
     * @param filtroTexto Texto para filtrar por nombre de producto o código
     * @param estados Lista de estados de verificación para filtrar
     * @param pageable Parámetros de paginación
     * @return Página de ítems de recepción
     */
    public Page<RecepcionMercaderiaItem> findByRecepcionMercaderiaIdPaginadosConEstados(
            Long recepcionId, String filtroTexto, List<EstadoVerificacion> estados, Pageable pageable) {
        
        if (recepcionId == null) {
            throw new IllegalArgumentException("ID de recepción es requerido");
        }

        // Construir query usando CriteriaBuilder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<RecepcionMercaderiaItem> query = cb.createQuery(RecepcionMercaderiaItem.class);
        Root<RecepcionMercaderiaItem> root = query.from(RecepcionMercaderiaItem.class);
        
        // Lista de predicados para los filtros
        List<Predicate> predicates = new ArrayList<>();
        
        // Filtro por recepción de mercadería
        predicates.add(cb.equal(root.get("recepcionMercaderia").get("id"), recepcionId));
        
        // Filtro por texto (nombre de producto o código)
        if (filtroTexto != null && !filtroTexto.trim().isEmpty()) {
            String filtroLower = filtroTexto.toLowerCase();
            predicates.add(cb.or(
                cb.like(cb.lower(root.get("notaRecepcionItem").get("producto").get("descripcion")), "%" + filtroLower + "%"),
                cb.like(root.get("notaRecepcionItem").get("producto").get("id").as(String.class), "%" + filtroTexto + "%")
            ));
        }
        
        // Filtro por array de estados
        if (estados != null && !estados.isEmpty()) {
            predicates.add(root.get("estadoVerificacion").in(estados));
        }
        
        // Aplicar todos los predicados
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(new Predicate[0]));
        }
        
        // Ordenar por ID ascendente
        query.orderBy(cb.asc(root.get("id")));
        
        // Ejecutar query con paginación
        TypedQuery<RecepcionMercaderiaItem> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<RecepcionMercaderiaItem> content = typedQuery.getResultList();
        
        // Contar total de resultados para paginación
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<RecepcionMercaderiaItem> countRoot = countQuery.from(RecepcionMercaderiaItem.class);
        countQuery.select(cb.count(countRoot));
        
        if (!predicates.isEmpty()) {
            countQuery.where(predicates.toArray(new Predicate[0]));
        }
        
        Long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        
        // Crear objeto Page
        return new PageImpl<>(content, pageable, totalElements);
    }

    /**
     * Busca un item pendiente de recepción por producto en una recepción específica
     * @param recepcionId ID de la recepción de mercadería
     * @param productoId ID del producto
     * @return Item pendiente de recepción o null si no se encuentra
     */
    public RecepcionMercaderiaItem findPendienteRecepcionItemPorProducto(Long recepcionId, Long productoId) {
        if (recepcionId == null || productoId == null) {
            throw new IllegalArgumentException("ID de recepción y producto son requeridos");
        }
        
        return repository.findPendienteRecepcionItemPorProducto(recepcionId, productoId);
    }

    /**
     * Obtiene el sumario de una recepción de mercadería
     * @param recepcionId ID de la recepción
     * @return DTO con el sumario de la recepción
     */
    public RecepcionSumarioDTO obtenerSumarioRecepcion(Long recepcionId) {
        if (recepcionId == null) {
            throw new IllegalArgumentException("ID de recepción es requerido");
        }

        try {
            return repository.findSumarioRecepcion(recepcionId);
        } catch (Exception e) {
            Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
            logger.error("Error al obtener sumario de recepción: " + recepcionId, e);
            throw new RuntimeException("Error al obtener sumario de recepción", e);
        }
    }
} 