package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.TypedQuery;
import org.springframework.data.domain.PageImpl;

@Service
@RequiredArgsConstructor
public class RecepcionMercaderiaService extends CrudService<RecepcionMercaderia, RecepcionMercaderiaRepository, Long> {
    
    private static final Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaService.class);
    
    private final RecepcionMercaderiaRepository repository;
    
    // Usar @Lazy para romper dependencia circular con RecepcionMercaderiaItemService
    @Autowired
    @Lazy
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;
    
    @Autowired
    private RecepcionMercaderiaNotaService recepcionMercaderiaNotaService;
    
    @Autowired
    private RecepcionCostoAdicionalService recepcionCostoAdicionalService;
    
    @Autowired
    private MovimientoStockService movimientoStockService;
    
    @Autowired
    private CostosPorProductoService costoPorProductoService;
    
    @Autowired
    private ProcesoEtapaService procesoEtapaService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    @Lazy
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EntityManager entityManager;

    @Override
    public RecepcionMercaderiaRepository getRepository() {
        return repository;
    }

    /**
     * Crea una nueva recepción de mercadería
     */
    @Transactional
    public RecepcionMercaderia crearRecepcion(Proveedor proveedor, Sucursal sucursal, 
                                             Moneda moneda, Double cotizacion, Usuario usuario) {
        RecepcionMercaderia recepcion = new RecepcionMercaderia();
        recepcion.setProveedor(proveedor);
        recepcion.setSucursalRecepcion(sucursal);
        recepcion.setFecha(LocalDateTime.now());
        recepcion.setMoneda(moneda);
        recepcion.setCotizacion(cotizacion);
        recepcion.setEstado(RecepcionMercaderiaEstado.EN_PROCESO);
        recepcion.setUsuario(usuario);
        
        return save(recepcion);
    }

    /**
     * Asocia notas de recepción a una recepción de mercadería.
     * Las notas en estado CONCILIADA pasan a EN_RECEPCION (recepción física en curso).
     */
    @Transactional
    public void asociarNotasRecepcion(Long recepcionId, List<Long> notaRecepcionIds) {
        RecepcionMercaderia recepcion = findById(recepcionId).orElseThrow(
            () -> new IllegalArgumentException("Recepción no encontrada: " + recepcionId)
        );
        
        for (Long notaId : notaRecepcionIds) {
            recepcionMercaderiaNotaService.asociarNotaARecepcion(recepcion, notaId);
            notaRecepcionService.findById(notaId).ifPresent(nota -> {
                if (nota.getEstado() == NotaRecepcionEstado.CONCILIADA) {
                    nota.setEstado(NotaRecepcionEstado.EN_RECEPCION);
                    notaRecepcionService.save(nota);
                }
            });
        }
    }

    /**
     * Finaliza una recepción de mercadería (sin rechazar pendientes)
     *
     * Usado en:
     * - Desktop: Sí (finalizarRecepcionFisicaPorPedido)
     */
    @Transactional
    public RecepcionMercaderia finalizarRecepcion(Long recepcionId) {
        return finalizarRecepcion(recepcionId, null);
    }

    /**
     * Finaliza una recepción de mercadería - FUNCIÓN CRÍTICA
     * Genera movimientos de stock y actualiza costos para todos los ítems
     *
     * Usado en:
     * - Desktop: Sí (finalizarRecepcionFisicaPorPedido)
     * - Mobile: Sí (finalizarRecepcionMercaderia)
     *
     * @param recepcionId ID de la recepción
     * @param motivoRechazoPendientes Si no es null, los items con cantidad pendiente se marcan como rechazados con este motivo antes de finalizar
     */
    @Transactional
    public RecepcionMercaderia finalizarRecepcion(Long recepcionId, MotivoRechazoFisico motivoRechazoPendientes) {
        RecepcionMercaderia recepcion = findById(recepcionId).orElseThrow(
            () -> new IllegalArgumentException("Recepción no encontrada: " + recepcionId)
        );
        
        if (recepcion.getEstado() == RecepcionMercaderiaEstado.FINALIZADA) {
            throw new IllegalStateException("La recepción ya está finalizada");
        }
        
        // Obtener todos los ítems de la recepción
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService
            .findByRecepcionMercaderiaId(recepcionId);
        
        // Si se indica motivo de rechazo para pendientes, marcar las cantidades pendientes como rechazadas
        if (motivoRechazoPendientes != null) {
            aplicarRechazoPendientes(items, motivoRechazoPendientes);
        }
        
        if (items.isEmpty()) {
            throw new IllegalStateException("No se pueden finalizar recepciones sin ítems");
        }
        
        // Obtener costos adicionales para prorrateo
        List<RecepcionCostoAdicional> costosAdicionales = recepcionCostoAdicionalService
            .findByRecepcionMercaderiaId(recepcionId);
        
        Double totalCostosAdicionales = calcularTotalCostosAdicionales(costosAdicionales, recepcion.getMoneda());
        Double totalValorItems = calcularTotalValorItems(items);
        
        // Procesar cada ítem: generar movimiento de stock y actualizar costo
        for (RecepcionMercaderiaItem item : items) {
            procesarItemRecepcion(item, totalCostosAdicionales, totalValorItems, recepcion);
        }
        
        // Cambiar estado de la recepción
        recepcion.setEstado(RecepcionMercaderiaEstado.FINALIZADA);
        RecepcionMercaderia recepcionFinalizada = save(recepcion);
        
        // Pasar las notas vinculadas a RECEPCION_COMPLETA (recepción física finalizada)
        List<RecepcionMercaderiaNota> asociaciones = recepcionMercaderiaNotaService.findByRecepcionMercaderiaId(recepcionId);
        for (RecepcionMercaderiaNota rmn : asociaciones) {
            if (rmn.getNotaRecepcion() != null && rmn.getNotaRecepcion().getId() != null) {
                notaRecepcionService.findById(rmn.getNotaRecepcion().getId()).ifPresent(nota -> {
                    nota.setEstado(NotaRecepcionEstado.RECEPCION_COMPLETA);
                    notaRecepcionService.save(nota);
                });
            }
        }
        
        // NO actualizar etapas del proceso aquí automáticamente
        // La actualización de etapas se hace en finalizarRecepcionFisicaPorPedido()
        // después de verificar que TODAS las recepciones del pedido estén finalizadas
        // Esto permite finalizar recepciones por sucursales sin bloquear otras sucursales
        
        return recepcionFinalizada;
    }

    /**
     * Procesa un ítem individual de recepción
     */
    @Transactional
    private void procesarItemRecepcion(RecepcionMercaderiaItem item, Double totalCostosAdicionales, 
                                     Double totalValorItems, RecepcionMercaderia recepcion) {
        
        // Solo procesar ítems que fueron realmente recibidos
        if (item.getCantidadRecibida() == null || item.getCantidadRecibida() <= 0) {
            return;
        }
        
        // Calcular costo unitario final (incluyendo costos adicionales prorrateados)
        Double costoUnitarioFinal = calcularCostoUnitarioFinal(item, totalCostosAdicionales, 
                                                              totalValorItems, recepcion);
        
        // Generar movimiento de stock (entrada positiva)
        generarMovimientoStock(item, costoUnitarioFinal, recepcion);
        
        // Actualizar o crear costo por producto (solo si no es bonificación)
        if (!item.getEsBonificacion()) {
            actualizarCostoPorProducto(item, costoUnitarioFinal, recepcion);
        }
    }

    /**
     * Calcula el costo unitario final incluyendo costos adicionales prorrateados
     */
    private Double calcularCostoUnitarioFinal(RecepcionMercaderiaItem item, Double totalCostosAdicionales, 
                                            Double totalValorItems, RecepcionMercaderia recepcion) {
        
        // Si es bonificación, el costo es 0
        if (item.getEsBonificacion()) {
            return 0.0;
        }
        
        // Obtener precio base del ítem de la nota
        Double precioBase = item.getNotaRecepcionItem().getPrecioUnitarioEnNota();
        if (precioBase == null) {
            precioBase = 0.0;
        }
        
        // Convertir a moneda de la recepción si es necesario
        Double precioBaseEnMonedaRecepcion = convertirMoneda(precioBase, 
            item.getNotaRecepcionItem().getNotaRecepcion().getMoneda(),
            recepcion.getMoneda(),
            item.getNotaRecepcionItem().getNotaRecepcion().getCotizacion(),
            recepcion.getCotizacion());
        
        // Calcular prorrateo de costos adicionales
        Double costoAdicionalPorItem = 0.0;
        if (totalCostosAdicionales > 0 && totalValorItems > 0) {
            Double valorItem = precioBaseEnMonedaRecepcion * item.getCantidadRecibida();
            Double porcentajeItem = valorItem / totalValorItems;
            costoAdicionalPorItem = (totalCostosAdicionales * porcentajeItem) / item.getCantidadRecibida();
        }
        
        return precioBaseEnMonedaRecepcion + costoAdicionalPorItem;
    }

    /**
     * Genera el movimiento de stock para un ítem recibido
     * Updated to use only available fields in MovimientoStock entity
     * IMPORTANTE: cantidadRecibida ya representa la cantidad neta aceptada (sin rechazos)
     * porque en el frontend se guardan por separado: cantidadRecibida (aceptada) y cantidadRechazada
     */
    private void generarMovimientoStock(RecepcionMercaderiaItem item, Double costoUnitario, 
                                      RecepcionMercaderia recepcion) {
        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setProducto(item.getProducto());
        movimiento.setSucursalId(item.getSucursalEntrega().getId());
        movimiento.setCantidad(item.getCantidadRecibida()); // MovimientoStock.cantidad is Double
        // cantidadRecibida ya es la cantidad neta aceptada (no incluye rechazos)
        movimiento.setTipoMovimiento(TipoMovimiento.COMPRA); // Entrada por compra
        movimiento.setReferencia(item.getId()); // Referencia al ítem de recepción
        movimiento.setCreadoEn(recepcion.getFecha()); // Use creadoEn instead of setFecha
        movimiento.setEstado(true); // Activo
        movimiento.setUsuario(recepcion.getUsuario());
        
        // Note: MovimientoStock doesn't have precioUnitario, vencimiento, lote, or observacion fields
        // These would need to be stored in a separate related entity if needed
        
        movimientoStockService.save(movimiento);
    }

    /**
     * Actualiza o crea el costo por producto
     * Updated to use existing methods in CostosPorProductoService
     */
    private void actualizarCostoPorProducto(RecepcionMercaderiaItem item, Double costoUnitario, 
                                          RecepcionMercaderia recepcion) {
        
        // Validaciones de campos requeridos
        if (item == null || item.getProducto() == null || item.getProducto().getId() == null) {
            throw new IllegalStateException("Item de recepción o producto no válido para actualizar costo");
        }
        
        if (item.getSucursalEntrega() == null || item.getSucursalEntrega().getId() == null) {
            throw new IllegalStateException("Sucursal de entrega no válida para actualizar costo");
        }
        
        if (recepcion == null || recepcion.getMoneda() == null) {
            throw new IllegalStateException("Recepción o moneda no válida para actualizar costo");
        }
        
        // Use existing method to find last cost for product
        // TODO: Need to create a method to find by producto and sucursal, or modify existing logic
        CostoPorProducto costoExistente = costoPorProductoService.findLastByProductoId(item.getProducto().getId());
        
        CostoPorProducto costo;
        // Verificar que costoExistente tenga sucursal y que coincida con la sucursal de entrega del item
        if (costoExistente != null && 
            costoExistente.getSucursal() != null && 
            costoExistente.getSucursal().getId() != null &&
            costoExistente.getSucursal().getId().equals(item.getSucursalEntrega().getId())) {
            // Update existing cost
            costo = costoExistente;
            // Calcular nuevo costo medio ponderado
            Double stockActual = movimientoStockService.stockByProductoIdAndSucursalId(
                item.getProducto().getId(), item.getSucursalEntrega().getId());
            Double stockAnterior = stockActual - item.getCantidadRecibida();
            
            if (stockAnterior > 0) {
                Double costoMedioAnterior = costo.getCostoMedio() != null ? costo.getCostoMedio() : 0.0;
                Double valorStockAnterior = stockAnterior * costoMedioAnterior;
                Double valorStockNuevo = item.getCantidadRecibida() * costoUnitario;
                Double nuevoCostoMedio = (valorStockAnterior + valorStockNuevo) / stockActual;
                costo.setCostoMedio(nuevoCostoMedio);
            } else {
                costo.setCostoMedio(costoUnitario);
            }
        } else {
            // Crear nuevo registro de costo
            costo = new CostoPorProducto();
            costo.setProducto(item.getProducto());
            costo.setSucursal(item.getSucursalEntrega());
            costo.setMoneda(recepcion.getMoneda());
            costo.setCostoMedio(costoUnitario);
            costo.setCreadoEn(LocalDateTime.now());
        }
        
        // Actualizar último precio de compra
        costo.setUltimoPrecioCompra(costoUnitario);
        costo.setCotizacion(recepcion.getCotizacion());
        // Usuario puede ser null, solo asignar si existe
        if (recepcion.getUsuario() != null) {
            costo.setUsuario(recepcion.getUsuario());
        }
        
        costoPorProductoService.save(costo);
    }

    /**
     * Marca como rechazadas las cantidades pendientes de cada ítem.
     * Cantidad pendiente = cantidad esperada - cantidadRecibida - cantidadRechazada
     */
    private void aplicarRechazoPendientes(List<RecepcionMercaderiaItem> items, MotivoRechazoFisico motivoRechazo) {
        for (RecepcionMercaderiaItem item : items) {
            Double cantidadEsperada = obtenerCantidadEsperada(item);
            Double cantidadRecibida = item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0.0;
            Double cantidadRechazada = item.getCantidadRechazada() != null ? item.getCantidadRechazada() : 0.0;
            Double pendiente = cantidadEsperada - cantidadRecibida - cantidadRechazada;

            if (pendiente > 0) {
                item.setCantidadRechazada(cantidadRechazada + pendiente);
                item.setMotivoRechazo(motivoRechazo);
                recepcionMercaderiaItemService.save(item);
            }
        }
    }

    /**
     * Obtiene la cantidad esperada para un ítem (desde distribución o nota)
     */
    private Double obtenerCantidadEsperada(RecepcionMercaderiaItem item) {
        if (item.getNotaRecepcionItemDistribucion() != null && item.getNotaRecepcionItemDistribucion().getCantidad() != null) {
            return item.getNotaRecepcionItemDistribucion().getCantidad();
        }
        if (item.getNotaRecepcionItem() != null && item.getNotaRecepcionItem().getCantidadEnNota() != null) {
            return item.getNotaRecepcionItem().getCantidadEnNota();
        }
        return 0.0;
    }

    /**
     * Calcula el total de costos adicionales convertidos a la moneda de la recepción
     */
    private Double calcularTotalCostosAdicionales(List<RecepcionCostoAdicional> costosAdicionales, 
                                                 Moneda monedaRecepcion) {
        return costosAdicionales.stream()
            .mapToDouble(costo -> convertirMoneda(costo.getMonto(), costo.getMoneda(), 
                                                 monedaRecepcion, 1.0, 1.0)) // Simplificado - asumir misma moneda
            .sum();
    }

    /**
     * Calcula el total del valor de todos los ítems
     */
    private Double calcularTotalValorItems(List<RecepcionMercaderiaItem> items) {
        return items.stream()
            .filter(item -> !item.getEsBonificacion() && item.getCantidadRecibida() > 0)
            .mapToDouble(item -> {
                Double precio = item.getNotaRecepcionItem().getPrecioUnitarioEnNota();
                return (precio != null ? precio : 0.0) * item.getCantidadRecibida();
            })
            .sum();
    }

    /**
     * Convierte entre monedas (simplificado - en producción usar servicio de conversión)
     */
    private Double convertirMoneda(Double monto, Moneda monedaOrigen, Moneda monedaDestino, 
                                  Double cotizacionOrigen, Double cotizacionDestino) {
        // Por ahora asumir que todas las monedas son iguales
        // En producción implementar lógica de conversión real
        return monto;
    }

    /**
     * Actualiza las etapas del proceso para todos los pedidos relacionados
     */
    private void actualizarEtapasProceso(RecepcionMercaderia recepcion) {
        // Obtener todas las notas asociadas a esta recepción
        List<RecepcionMercaderiaNota> notasAsociadas = recepcionMercaderiaNotaService
            .findByRecepcionMercaderiaId(recepcion.getId());
        
        for (RecepcionMercaderiaNota notaAsociada : notasAsociadas) {
            NotaRecepcion nota = notaAsociada.getNotaRecepcion();
            if (nota.getPedido() != null) {
                // Finalizar etapa de recepción de mercadería para este pedido
                procesoEtapaService.finalizarEtapa(nota.getPedido().getId(), 
                                                 ProcesoEtapaTipo.RECEPCION_MERCADERIA);
                
                // Crear la siguiente etapa (Pago) como pendiente
                procesoEtapaService.crearEtapaSiguiente(nota.getPedido(), 
                                                      ProcesoEtapaTipo.RECEPCION_MERCADERIA);
            }
        }
    }

    /**
     * Busca recepciones con filtros usando QueryBuilder para mayor control
     */
    public Page<RecepcionMercaderia> findByFilters(Long proveedorId, Long sucursalId, 
                                                  RecepcionMercaderiaEstado estado,
                                                  List<RecepcionMercaderiaEstado> estados,
                                                  Long usuarioId,
                                                  LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                  Pageable pageable) {
        
        // Construir query usando CriteriaBuilder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<RecepcionMercaderia> query = cb.createQuery(RecepcionMercaderia.class);
        Root<RecepcionMercaderia> root = query.from(RecepcionMercaderia.class);
        
        // Lista de predicados para los filtros
        List<Predicate> predicates = new ArrayList<>();
        
        // Filtro por proveedor
        if (proveedorId != null) {
            predicates.add(cb.equal(root.get("proveedor").get("id"), proveedorId));
        }
        
        // Filtro por sucursal
        if (sucursalId != null) {
            predicates.add(cb.equal(root.get("sucursalRecepcion").get("id"), sucursalId));
        }
        
        // Filtro por estado individual
        if (estado != null) {
            predicates.add(cb.equal(root.get("estado"), estado));
        }
        
        // Filtro por lista de estados
        if (estados != null && !estados.isEmpty()) {
            predicates.add(root.get("estado").in(estados));
        }
        
        // Filtro por usuario
        if (usuarioId != null) {
            predicates.add(cb.equal(root.get("usuario").get("id"), usuarioId));
        }
        
        // Filtro por fecha inicio
        if (fechaInicio != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("fecha"), fechaInicio));
        }
        
        // Filtro por fecha fin
        if (fechaFin != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("fecha"), fechaFin));
        }
        
        // Aplicar todos los predicados
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(new Predicate[0]));
        }
        
        // Ordenar por fecha descendente
        query.orderBy(cb.desc(root.get("fecha")));
        
        // Log de debug: Query generada y predicados aplicados
        System.out.println("🔍 [Service] Query generada: " + query.toString());
        System.out.println("🔍 [Service] Predicados aplicados: " + predicates.size());
        System.out.println("🔍 [Service] Filtros: proveedorId=" + proveedorId + 
                          ", sucursalId=" + sucursalId + 
                          ", estados=" + estados + 
                          ", usuarioId=" + usuarioId);
        
        // Ejecutar query con paginación
        TypedQuery<RecepcionMercaderia> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<RecepcionMercaderia> content = typedQuery.getResultList();
        
        // Contar total de resultados para paginación
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<RecepcionMercaderia> countRoot = countQuery.from(RecepcionMercaderia.class);
        countQuery.select(cb.count(countRoot));
        
        if (!predicates.isEmpty()) {
            countQuery.where(predicates.toArray(new Predicate[0]));
        }
        
        Long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        
        // Log de resultados
        System.out.println("🔍 [Service] Resultados encontrados: " + content.size() + 
                          ", Total: " + totalElements);
        
        // Crear objeto Page
        return new PageImpl<>(content, pageable, totalElements);
    }

    /**
     * Obtiene recepciones por proveedor
     */
    public List<RecepcionMercaderia> findByProveedorId(Long proveedorId) {
        return repository.findByProveedorId(proveedorId);
    }

    /**
     * Obtiene recepciones por estado
     */
    public List<RecepcionMercaderia> findByEstado(RecepcionMercaderiaEstado estado) {
        return repository.findByEstado(estado);
    }

    /**
     * Busca recepciones existentes por criterios de reutilización
     * Regla: Mismo proveedor, misma sucursal, misma fecha, estado EN_PROCESO/PENDIENTE
     */
    public List<RecepcionMercaderia> findRecepcionesReutilizables(
            Long proveedorId, Long sucursalRecepcionId, LocalDateTime fecha, Long usuarioId) {
        return repository.findRecepcionesReutilizables(proveedorId, sucursalRecepcionId, fecha, usuarioId);
    }

    /**
     * Obtiene o crea una recepción según las reglas de negocio
     * Regla 1: Reutilizar si existe con mismos criterios
     * Regla 2: Crear nueva si no existe o criterios diferentes
     */
    @Transactional
    public RecepcionMercaderia obtenerOcrearRecepcion(
            Long proveedorId, Long sucursalRecepcionId, Long monedaId, 
            Double cotizacion, Long usuarioId) {
        
        // Normalizar fecha a día completo (00:00:00)
        LocalDateTime fechaNormalizada = LocalDateTime.now()
            .withHour(0).withMinute(0).withSecond(0).withNano(0);
        
        // Buscar recepciones reutilizables
        List<RecepcionMercaderia> recepcionesExistentes = findRecepcionesReutilizables(
            proveedorId, sucursalRecepcionId, fechaNormalizada, usuarioId);
        
        if (!recepcionesExistentes.isEmpty()) {
            // Reutilizar la primera recepción encontrada
            RecepcionMercaderia recepcionExistente = recepcionesExistentes.get(0);
            logger.info("Reutilizando recepción existente ID: {} para proveedor: {}, sucursal: {}, fecha: {}", 
                recepcionExistente.getId(), proveedorId, sucursalRecepcionId, fechaNormalizada);
            return recepcionExistente;
        } else {
            // Crear nueva recepción
            Proveedor proveedor = proveedorService.findById(proveedorId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado: " + proveedorId));
            
            Sucursal sucursal = sucursalService.findById(sucursalRecepcionId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada: " + sucursalRecepcionId));
            
            Moneda moneda = monedaService.findById(monedaId)
                .orElseThrow(() -> new RuntimeException("Moneda no encontrada: " + monedaId));
            
            Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + usuarioId));
            
            RecepcionMercaderia nuevaRecepcion = crearRecepcion(proveedor, sucursal, moneda, cotizacion, usuario);
            logger.info("Creada nueva recepción ID: {} para proveedor: {}, sucursal: {}, fecha: {}", 
                nuevaRecepcion.getId(), proveedorId, sucursalRecepcionId, fechaNormalizada);
            return nuevaRecepcion;
        }
    }

    /**
     * Verifica si un ítem de nota ya tiene recepción para una sucursal específica
     */
    public boolean existeRecepcionParaNotaItemYSucursal(Long notaRecepcionItemId, Long sucursalEntregaId) {
        return repository.existeRecepcionParaNotaItemYSucursal(notaRecepcionItemId, sucursalEntregaId);
    }

    /**
     * Busca recepciones por pedidoId
     */
    public List<RecepcionMercaderia> findByPedidoId(Long pedidoId) {
        return repository.findByPedidoId(pedidoId);
    }

    /**
     * Verifica si existe una recepción para una nota en una sucursal específica
     * Bloquea recepciones EN_PROCESO, PENDIENTE y FINALIZADAS para evitar duplicación de stock y costos
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (validación antes de iniciar recepción)
     * 
     * @param notaRecepcionId ID de la nota de recepción
     * @param sucursalRecepcionId ID de la sucursal de recepción
     * @return RecepcionMercaderia si existe (EN_PROCESO, PENDIENTE o FINALIZADA), null si no existe
     */
    public RecepcionMercaderia encontrarRecepcionActivaPorNotaYSucursal(Long notaRecepcionId, Long sucursalRecepcionId) {
        // Obtener todas las asociaciones de la nota
        List<RecepcionMercaderiaNota> asociaciones = recepcionMercaderiaNotaService.findByNotaRecepcionId(notaRecepcionId);
        
        // Filtrar por sucursal y estado (bloquear EN_PROCESO, PENDIENTE y FINALIZADA)
        // FINALIZADA se bloquea porque ya generó movimientos de stock y costos
        for (RecepcionMercaderiaNota asociacion : asociaciones) {
            RecepcionMercaderia recepcion = asociacion.getRecepcionMercaderia();
            if (recepcion != null && 
                recepcion.getSucursalRecepcion() != null &&
                recepcion.getSucursalRecepcion().getId().equals(sucursalRecepcionId) &&
                (recepcion.getEstado() == RecepcionMercaderiaEstado.EN_PROCESO || 
                 recepcion.getEstado() == RecepcionMercaderiaEstado.PENDIENTE ||
                 recepcion.getEstado() == RecepcionMercaderiaEstado.FINALIZADA)) {
                return recepcion;
            }
        }
        
        return null;
    }
} 