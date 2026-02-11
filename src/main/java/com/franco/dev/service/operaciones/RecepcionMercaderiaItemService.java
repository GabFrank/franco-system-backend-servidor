package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.graphql.operaciones.dto.RecepcionSumarioDTO;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class RecepcionMercaderiaItemService extends CrudService<RecepcionMercaderiaItem, RecepcionMercaderiaItemRepository, Long> {
    private final RecepcionMercaderiaItemRepository repository;
    private final EntityManager entityManager;
    private final NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;
    private final RecepcionMercaderiaNotaService recepcionMercaderiaNotaService;
    private final MovimientoStockRepository movimientoStockRepository;
    private final MovimientoStockService movimientoStockService;
    
    // Usar @Lazy para romper dependencia circular con RecepcionMercaderiaService
    @Autowired
    @Lazy
    private RecepcionMercaderiaService recepcionMercaderiaService;

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
        return calcularEstadoRecepcion(items, null);
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
        return calcularEstadoRecepcion(items, sucursalesIds);
    }

    /**
     * Obtiene el estado de recepción para una distribución específica
     * @param distribucionId ID del NotaRecepcionItemDistribucion
     * @return Estado de recepción física: PENDIENTE, VERIFICADO, RECHAZADO, PARCIAL
     */
    public String getEstadoRecepcionPorDistribucion(Long distribucionId) {
        List<RecepcionMercaderiaItem> items = repository.findByNotaRecepcionItemDistribucionId(distribucionId);
        
        if (items.isEmpty()) {
            return "PENDIENTE";
        }

        // Obtener cantidad esperada de la distribución
        com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion distribucion = 
                notaRecepcionItemDistribucionService.findById(distribucionId).orElse(null);
        
        if (distribucion == null) {
            return "PENDIENTE";
        }

        Double cantidadEsperada = distribucion.getCantidad() != null ? distribucion.getCantidad() : 0.0;
        
        // Usar lógica de cálculo de estado interna
        return calcularEstadoRecepcionInterno(items, cantidadEsperada);
    }

    /**
     * Lógica interna para calcular el estado basado en items y cantidad esperada
     */
    private String calcularEstadoRecepcionInterno(List<RecepcionMercaderiaItem> items, Double cantidadEsperada) {
        if (items.isEmpty()) return "PENDIENTE";
        
        Double cantidadRecibidaTotal = items.stream()
                .mapToDouble(item -> item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0)
                .sum();
        
        Double cantidadRechazadaTotal = items.stream()
                .mapToDouble(item -> item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0)
                .sum();

        Double TOLERANCIA = 0.001;
        if (cantidadRecibidaTotal == 0 && cantidadRechazadaTotal == 0) return "PENDIENTE";
        
        Double sumaTotal = cantidadRecibidaTotal + cantidadRechazadaTotal;
        Double diferencia = Math.abs(sumaTotal - cantidadEsperada);
        
        if (diferencia < TOLERANCIA) {
            return cantidadRecibidaTotal > 0 ? "VERIFICADO" : "RECHAZADO";
        }
        
        if (sumaTotal < cantidadEsperada) {
            return "PARCIAL";
        }
        
        // Si hay exceso, consideramos como verificado para efectos de UI
        return "VERIFICADO";
    }

    /**
     * Calcula el estado de recepción basado en una lista de RecepcionMercaderiaItem
     * @param items Lista de items de recepción de mercadería
     * @param sucursalesIdsFiltro Lista opcional de IDs de sucursales usadas para filtrar (si es null, usar sucursales de los items)
     * @return Estado de recepción física: PENDIENTE, VERIFICADO, RECHAZADO, PARCIAL
     */
    private String calcularEstadoRecepcion(List<RecepcionMercaderiaItem> items, List<Long> sucursalesIdsFiltro) {
        if (items.isEmpty()) {
            return "PENDIENTE";
        }

        // Calcular totales sumando todos los items
        Double cantidadRecibidaTotal = items.stream()
                .mapToDouble(item -> item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0)
                .sum();
        
        Double cantidadRechazadaTotal = items.stream()
                .mapToDouble(item -> item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0)
                .sum();

        // Obtener el NotaRecepcionItem desde el primer item (todos deberían tener el mismo)
        Long notaRecepcionItemId = items.get(0).getNotaRecepcionItem() != null 
                ? items.get(0).getNotaRecepcionItem().getId() 
                : null;
        
        // Obtener cantidad esperada desde las distribuciones del NotaRecepcionItem original
        Double cantidadEsperada = 0.0;
        if (notaRecepcionItemId != null) {
            // Obtener todas las distribuciones del NotaRecepcionItem
            List<com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion> distribuciones = 
                    notaRecepcionItemDistribucionService.findByNotaRecepcionItemId(notaRecepcionItemId);
            
            // Determinar qué sucursales usar para filtrar las distribuciones
            Set<Long> sucursalesIdsParaFiltrar;
            if (sucursalesIdsFiltro != null && !sucursalesIdsFiltro.isEmpty()) {
                // Usar las sucursales del filtro (más confiable)
                sucursalesIdsParaFiltrar = new java.util.HashSet<>(sucursalesIdsFiltro);
            } else {
                // Si no hay filtro, obtener las sucursales únicas de los items
                sucursalesIdsParaFiltrar = items.stream()
                        .filter(item -> item.getSucursalEntrega() != null && item.getSucursalEntrega().getId() != null)
                        .map(item -> item.getSucursalEntrega().getId())
                        .collect(Collectors.toSet());
            }
            
            if (!sucursalesIdsParaFiltrar.isEmpty()) {
                // Filtrar distribuciones por las sucursales
                cantidadEsperada = distribuciones.stream()
                        .filter(dist -> dist.getSucursalEntrega() != null 
                                && sucursalesIdsParaFiltrar.contains(dist.getSucursalEntrega().getId()))
                        .mapToDouble(dist -> dist.getCantidad() != null ? dist.getCantidad() : 0.0)
                        .sum();
            } else {
                // Si no hay sucursales para filtrar, sumar todas las distribuciones
                cantidadEsperada = distribuciones.stream()
                        .mapToDouble(dist -> dist.getCantidad() != null ? dist.getCantidad() : 0.0)
                        .sum();
            }
        }

        // Si no hay cantidad esperada calculada desde distribuciones, usar lógica anterior como fallback
        if (cantidadEsperada == null || cantidadEsperada <= 0) {
            // Lógica anterior como fallback
            if (cantidadRechazadaTotal > 0) {
                return "RECHAZADO";
            }
            if (cantidadRecibidaTotal > 0) {
                // Verificar si todos los items tienen cantidad recibida
                boolean todosVerificados = items.stream()
                        .allMatch(item -> item.getCantidadRecibida() != null && item.getCantidadRecibida() > 0);
                return todosVerificados ? "VERIFICADO" : "PARCIAL";
            }
            return "PENDIENTE";
        }

        // Tolerancia para comparaciones de punto flotante
        Double TOLERANCIA = 0.001;

        // Si no se ha procesado nada
        if (cantidadRecibidaTotal == 0 && cantidadRechazadaTotal == 0) {
            return "PENDIENTE";
        }

        // Si toda la cantidad esperada fue rechazada
        if (cantidadRechazadaTotal >= cantidadEsperada - TOLERANCIA) {
            return "RECHAZADO";
        }

        // Log para debugging
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("=== CALCULANDO ESTADO DE RECEPCIÓN ===");
        logger.info("NotaRecepcionItemId: {}", notaRecepcionItemId);
        logger.info("Cantidad Esperada: {}", cantidadEsperada);
        logger.info("Cantidad Recibida Total: {}", cantidadRecibidaTotal);
        logger.info("Cantidad Rechazada Total: {}", cantidadRechazadaTotal);
        logger.info("Suma (Recibida + Rechazada): {}", (cantidadRecibidaTotal + cantidadRechazadaTotal));
        logger.info("Diferencia: {}", Math.abs((cantidadRecibidaTotal + cantidadRechazadaTotal) - cantidadEsperada));
        
        // Calcular cantidad pendiente y diferencia
        Double cantidadPendiente = cantidadEsperada - cantidadRecibidaTotal - cantidadRechazadaTotal;
        Double sumaTotal = cantidadRecibidaTotal + cantidadRechazadaTotal;
        Double diferencia = Math.abs(sumaTotal - cantidadEsperada);
        
        logger.info("Cantidad Pendiente: {}", cantidadPendiente);
        logger.info("Suma (Recibida + Rechazada): {}", sumaTotal);
        logger.info("Diferencia: {}", diferencia);
        
        // PRIORIDAD 1: Si la suma de recibido + rechazado cubre toda la cantidad esperada (dentro de tolerancia)
        if (diferencia < TOLERANCIA) {
            logger.info("La suma cubre la cantidad esperada. Diferencia: {}", diferencia);
            // Si hay cantidad recibida, está verificado (aunque haya rechazo)
            if (cantidadRecibidaTotal > 0) {
                logger.info("Estado: VERIFICADO (hay cantidad recibida y suma completa)");
                return "VERIFICADO";
            } else {
                // Solo rechazo, completamente rechazado
                logger.info("Estado: RECHAZADO (solo rechazo y suma completa)");
                return "RECHAZADO";
            }
        }
        
        // PRIORIDAD 2: Si la cantidad pendiente es muy pequeña (dentro de tolerancia), considerar como completo
        if (Math.abs(cantidadPendiente) < TOLERANCIA) {
            logger.info("Cantidad pendiente dentro de tolerancia. Diferencia: {}", cantidadPendiente);
            if (cantidadRecibidaTotal > 0) {
                logger.info("Estado: VERIFICADO (diferencia por redondeo, hay cantidad recibida)");
                return "VERIFICADO";
            } else if (cantidadRechazadaTotal > 0) {
                logger.info("Estado: RECHAZADO (diferencia por redondeo, solo rechazo)");
                return "RECHAZADO";
            }
        }
        
        // PRIORIDAD 3: Si hay cantidad pendiente significativa, es parcial
        if (cantidadPendiente > TOLERANCIA) {
            logger.info("Hay cantidad pendiente significativa. Estado: PARCIAL");
            // Si hay rechazo parcial, el estado puede ser PARCIAL o RECHAZADO según la lógica de negocio
            // Por ahora, si hay rechazo parcial, usamos PARCIAL para indicar que hay cantidad pendiente
            if (cantidadRechazadaTotal > 0) {
                return "PARCIAL"; // Rechazo parcial, hay cantidad pendiente
            } else if (cantidadRecibidaTotal > 0) {
                return "PARCIAL"; // Recepción parcial, hay cantidad pendiente
            }
        }
        
        // PRIORIDAD 4: Si la cantidad pendiente es negativa (más recibido/rechazado que esperado)
        // Esto puede ocurrir por errores de datos, pero aún así debemos determinar un estado
        if (cantidadPendiente < -TOLERANCIA) {
            logger.warn("Cantidad pendiente negativa (más recibido/rechazado que esperado). Diferencia: {}", cantidadPendiente);
            // Si hay cantidad recibida, considerar como verificado (aunque haya exceso)
            if (cantidadRecibidaTotal > 0) {
                logger.info("Estado: VERIFICADO (hay exceso pero hay cantidad recibida)");
                return "VERIFICADO";
            } else if (cantidadRechazadaTotal > 0) {
                logger.info("Estado: RECHAZADO (hay exceso pero solo rechazo)");
                return "RECHAZADO";
            }
        }

        // Caso edge: no debería ocurrir si la lógica está correcta
        logger.warn("Estado no determinado después de todas las verificaciones. Retornando PENDIENTE");
        logger.warn("Valores: cantidadEsperada={}, cantidadRecibidaTotal={}, cantidadRechazadaTotal={}, cantidadPendiente={}, diferencia={}", 
            cantidadEsperada, cantidadRecibidaTotal, cantidadRechazadaTotal, cantidadPendiente, diferencia);
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
            
            // Forzar flush para que countByRecepcionMercaderiaId sea preciso
            repository.flush();
            
            // 4. Verificar si la RecepcionMercaderia queda vacía
            if (recepcionMercaderia != null) {
                Long cantidadItems = repository.countByRecepcionMercaderiaId(recepcionMercaderia.getId());
                logger.info("Cantidad de items restantes en recepción {}: {}", recepcionMercaderia.getId(), cantidadItems);
                
                if (cantidadItems == 0) {
                    logger.info("RecepcionMercaderia {} está vacía, será eliminada", recepcionMercaderia.getId());
                    // Eliminar asociaciones de notas primero (incluye flush)
                    recepcionMercaderiaNotaService.deleteByRecepcionId(recepcionMercaderia.getId());
                    
                    // Eliminar cabezal usando entityManager para asegurar que se procese en el orden correcto
                    RecepcionMercaderia managedRecepcion = entityManager.find(RecepcionMercaderia.class, recepcionMercaderia.getId());
                    if (managedRecepcion != null) {
                        entityManager.remove(managedRecepcion);
                        entityManager.flush(); // Forzar eliminación del cabezal
                    }
                    logger.info("RecepcionMercaderia {} eliminada correctamente", recepcionMercaderia.getId());
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
     * Obtiene la fecha de finalización de una recepción basándose en los movimientos de stock
     * @param recepcionMercaderiaId ID de la recepción
     * @return LocalDateTime de la fecha más reciente de los movimientos de stock de tipo COMPRA, o null si no hay movimientos
     */
    private LocalDateTime obtenerFechaFinalizacion(Long recepcionMercaderiaId) {
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("Obteniendo fecha de finalización para recepción: {}", recepcionMercaderiaId);
        
        // 1. Obtener todos los RecepcionMercaderiaItem de la recepción
        List<RecepcionMercaderiaItem> items = repository.findByRecepcionMercaderiaId(recepcionMercaderiaId);
        
        if (items.isEmpty()) {
            logger.info("No hay items en la recepción, no se puede determinar fecha de finalización");
            return null;
        }
        
        // 2. Para cada item, buscar movimientos de stock de tipo COMPRA con referencia = item.id
        LocalDateTime fechaMasReciente = null;
        
        for (RecepcionMercaderiaItem item : items) {
            List<MovimientoStock> movimientos = movimientoStockRepository
                .findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, item.getId());
            
            for (MovimientoStock movimiento : movimientos) {
                if (movimiento.getCreadoEn() != null) {
                    if (fechaMasReciente == null || movimiento.getCreadoEn().isAfter(fechaMasReciente)) {
                        fechaMasReciente = movimiento.getCreadoEn();
                    }
                }
            }
        }
        
        logger.info("Fecha de finalización encontrada: {}", fechaMasReciente);
        return fechaMasReciente;
    }

    /**
     * Valida que no hayan pasado más de 24 horas desde la finalización de una recepción
     * @param recepcionMercaderiaId ID de la recepción
     * @throws IllegalStateException si han pasado más de 24 horas
     */
    private void validarTiempoDesdeFinalizacion(Long recepcionMercaderiaId) {
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("Validando tiempo desde finalización para recepción: {}", recepcionMercaderiaId);
        
        LocalDateTime fechaFinalizacion = obtenerFechaFinalizacion(recepcionMercaderiaId);
        if (fechaFinalizacion == null) {
            // Si no hay movimientos de stock, la recepción no fue realmente finalizada
            logger.info("No se encontraron movimientos de stock, se permite continuar");
            return; // Permitir continuar
        }
        
        LocalDateTime ahora = LocalDateTime.now();
        long horasTranscurridas = ChronoUnit.HOURS.between(fechaFinalizacion, ahora);
        
        logger.info("Horas transcurridas desde finalización: {}", horasTranscurridas);
        
        if (horasTranscurridas > 24) {
            String mensaje = String.format("No se puede reabrir una recepción finalizada hace más de 24 horas. " +
                         "Tiempo transcurrido: %d horas", horasTranscurridas);
            logger.warn(mensaje);
            throw new IllegalStateException(mensaje);
        }
        
        logger.info("Validación de tiempo exitosa, se puede reabrir la recepción");
    }

    /**
     * Resetea la verificación de un ítem de recepción (elimina variaciones y resetea estado)
     * @param recepcionMercaderiaItemId ID del RecepcionMercaderiaItem
     * @return true si la verificación fue reseteada, false en caso contrario
     */
    @Transactional
    public Boolean resetearVerificacion(Long recepcionMercaderiaItemId) {
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("=== Iniciando reseteo (ELIMINACIÓN) de verificación ===");
        logger.info("RecepcionMercaderiaItemId: {}", recepcionMercaderiaItemId);
        
        try {
            // 1. Buscar RecepcionMercaderiaItem por su ID
            RecepcionMercaderiaItem item = repository.findById(recepcionMercaderiaItemId)
                .orElse(null);
            
            if (item == null) {
                logger.warn("No se encontró RecepcionMercaderiaItem con ID: {}", recepcionMercaderiaItemId);
                return false;
            }
            
            RecepcionMercaderia recepcionMercaderia = item.getRecepcionMercaderia();
            
            // 2. Verificar estado de recepción y reabrir si está FINALIZADA
            if (recepcionMercaderia != null && recepcionMercaderia.getEstado() == RecepcionMercaderiaEstado.FINALIZADA) {
                logger.info("Recepción está FINALIZADA, validando tiempo desde finalización");
                // Validar que no hayan pasado más de 24 horas
                validarTiempoDesdeFinalizacion(recepcionMercaderia.getId());
                
                // Reabrir la recepción automáticamente
                logger.info("Reabriendo recepción {}", recepcionMercaderia.getId());
                recepcionMercaderia.setEstado(RecepcionMercaderiaEstado.EN_PROCESO);
                recepcionMercaderiaService.save(recepcionMercaderia);
                logger.info("Recepción reabierta exitosamente");
            }
            
            // 3. Buscar movimientos de stock por referencia (TipoMovimiento.COMPRA)
            logger.info("Buscando movimientos de stock para item {}", item.getId());
            List<MovimientoStock> movimientosStock = movimientoStockRepository
                .findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, item.getId());
            
            // 4. Eliminar movimientos de stock encontrados
            if (!movimientosStock.isEmpty()) {
                logger.info("Eliminando {} movimiento(s) de stock asociado(s)", movimientosStock.size());
                for (MovimientoStock movimiento : movimientosStock) {
                    movimientoStockService.delete(movimiento);
                    logger.info("Movimiento de stock {} eliminado", movimiento.getId());
                }
            } else {
                logger.info("No se encontraron movimientos de stock para eliminar");
            }
            
            logger.info("Eliminando RecepcionMercaderiaItem ID: {} - Producto: {}", 
                item.getId(), item.getProducto() != null ? item.getProducto().getDescripcion() : "N/A");
            
            // 5. Eliminar el item (Hibernate cascade borrará variaciones)
            repository.delete(item);
            
            // Forzar flush
            repository.flush();
            
            // 6. Verificar si la RecepcionMercaderia queda vacía
            if (recepcionMercaderia != null) {
                Long cantidadItems = repository.countByRecepcionMercaderiaId(recepcionMercaderia.getId());
                if (cantidadItems == 0) {
                    logger.info("RecepcionMercaderia {} está vacía, será eliminada", recepcionMercaderia.getId());
                    // Eliminar asociaciones de notas primero (incluye flush)
                    recepcionMercaderiaNotaService.deleteByRecepcionId(recepcionMercaderia.getId());
                    
                    // Eliminar cabezal usando entityManager para asegurar que se procese en el orden correcto
                    RecepcionMercaderia managedRecepcion = entityManager.find(RecepcionMercaderia.class, recepcionMercaderia.getId());
                    if (managedRecepcion != null) {
                        entityManager.remove(managedRecepcion);
                        entityManager.flush(); // Forzar eliminación del cabezal
                    }
                }
            }
            
            logger.info("=== Reseteo (eliminación) de verificación completado exitosamente ===");
            return true;
            
        } catch (IllegalStateException e) {
            // Re-lanzar excepciones de validación sin envolver
            throw e;
        } catch (Exception e) {
            logger.error("=== ERROR durante reseteo de verificación ===");
            logger.error("Exception: {}", e.getMessage(), e);
            throw new RuntimeException("Error al resetear verificación: " + e.getMessage());
        }
    }

    /**
     * Deshace la verificación de todos los items de un producto en una recepción
     * Útil cuando el producto proviene de múltiples notas (distribución automática)
     * @param recepcionMercaderiaId ID de la recepción
     * @param productoId ID del producto
     * @return true si se deshicieron las verificaciones, false en caso contrario
     */
    @Transactional
    public Boolean deshacerVerificacionPorProducto(Long recepcionMercaderiaId, Long productoId) {
        Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaItemService.class);
        logger.info("=== Iniciando deshacer verificación por producto ===");
        logger.info("RecepcionMercaderiaId: {}, ProductoId: {}", recepcionMercaderiaId, productoId);
        
        try {
            // 1. Verificar que existe la recepción
            RecepcionMercaderia recepcionMercaderia = recepcionMercaderiaService.findById(recepcionMercaderiaId)
                .orElse(null);
            
            if (recepcionMercaderia == null) {
                logger.warn("No se encontró RecepcionMercaderia con ID: {}", recepcionMercaderiaId);
                return false;
            }
            
            // 2. Verificar estado de recepción y reabrir si está FINALIZADA
            if (recepcionMercaderia.getEstado() == RecepcionMercaderiaEstado.FINALIZADA) {
                logger.info("Recepción está FINALIZADA, validando tiempo desde finalización");
                // Validar que no hayan pasado más de 24 horas
                validarTiempoDesdeFinalizacion(recepcionMercaderiaId);
                
                // Reabrir la recepción automáticamente
                logger.info("Reabriendo recepción {}", recepcionMercaderiaId);
                recepcionMercaderia.setEstado(RecepcionMercaderiaEstado.EN_PROCESO);
                recepcionMercaderiaService.save(recepcionMercaderia);
                logger.info("Recepción reabierta exitosamente");
            }
            
            // 3. Buscar todos los RecepcionMercaderiaItem del producto en la recepción
            List<RecepcionMercaderiaItem> items = repository.findByRecepcionMercaderiaId(recepcionMercaderiaId);
            List<RecepcionMercaderiaItem> itemsProducto = items.stream()
                .filter(item -> item.getProducto() != null && 
                               item.getProducto().getId() != null && 
                               item.getProducto().getId().equals(productoId))
                .collect(Collectors.toList());
            
            if (itemsProducto.isEmpty()) {
                logger.warn("No se encontraron items del producto {} en la recepción {}", productoId, recepcionMercaderiaId);
                return false;
            }
            
            logger.info("Se encontraron {} item(s) del producto {} para resetear", itemsProducto.size(), productoId);
            
            // 4. Para cada item: eliminar movimientos de stock y RESETEAR valores (no eliminar)
            // El item queda como pendiente para re-verificar o marcar como rechazado al finalizar
            for (RecepcionMercaderiaItem item : itemsProducto) {
                logger.info("Procesando item {} - Producto: {}", 
                    item.getId(), item.getProducto() != null ? item.getProducto().getDescripcion() : "N/A");
                
                // Buscar y eliminar movimientos de stock
                List<MovimientoStock> movimientosStock = movimientoStockRepository
                    .findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, item.getId());
                
                if (!movimientosStock.isEmpty()) {
                    logger.info("Eliminando {} movimiento(s) de stock para item {}", movimientosStock.size(), item.getId());
                    for (MovimientoStock movimiento : movimientosStock) {
                        movimientoStockService.delete(movimiento);
                        logger.info("Movimiento de stock {} eliminado", movimiento.getId());
                    }
                } else {
                    logger.info("No se encontraron movimientos de stock para item {}", item.getId());
                }
                
                // Resetear item a estado pendiente (no eliminar)
                item.setCantidadRecibida(0.0);
                item.setCantidadRechazada(0.0);
                item.setMotivoRechazo(null);
                item.setLote(null);
                item.setVencimientoRecibido(null);
                item.setMetodoVerificacion(null);
                item.setMotivoVerificacionManual(null);
                item.setEstadoVerificacion(EstadoVerificacion.PENDIENTE);
                if (item.getVariaciones() != null && !item.getVariaciones().isEmpty()) {
                    item.getVariaciones().clear();
                }
                repository.save(item);
                logger.info("RecepcionMercaderiaItem ID: {} reseteado a pendiente", item.getId());
            }
            
            repository.flush();
            
            logger.info("=== Deshacer verificación por producto completado exitosamente ===");
            return true;
            
        } catch (IllegalStateException e) {
            // Re-lanzar excepciones de validación sin envolver
            throw e;
        } catch (Exception e) {
            logger.error("=== ERROR durante deshacer verificación por producto ===");
            logger.error("Exception: {}", e.getMessage(), e);
            throw new RuntimeException("Error al deshacer verificación por producto: " + e.getMessage());
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
            
            // Forzar flush para que countByRecepcionMercaderiaId sea preciso
            repository.flush();
            
            // 4. Verificar si la RecepcionMercaderia queda vacía
            if (recepcionMercaderia != null) {
                Long cantidadItems = repository.countByRecepcionMercaderiaId(recepcionMercaderia.getId());
                logger.info("Cantidad de items restantes en recepción {}: {}", recepcionMercaderia.getId(), cantidadItems);
                
                if (cantidadItems == 0) {
                    logger.info("RecepcionMercaderia {} está vacía, será eliminada", recepcionMercaderia.getId());
                    // Eliminar asociaciones de notas primero (incluye flush)
                    recepcionMercaderiaNotaService.deleteByRecepcionId(recepcionMercaderia.getId());
                    
                    // Eliminar cabezal usando entityManager para asegurar que se procese en el orden correcto
                    RecepcionMercaderia managedRecepcion = entityManager.find(RecepcionMercaderia.class, recepcionMercaderia.getId());
                    if (managedRecepcion != null) {
                        entityManager.remove(managedRecepcion);
                        entityManager.flush(); // Forzar eliminación del cabezal
                    }
                    logger.info("RecepcionMercaderia {} eliminada correctamente", recepcionMercaderia.getId());
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