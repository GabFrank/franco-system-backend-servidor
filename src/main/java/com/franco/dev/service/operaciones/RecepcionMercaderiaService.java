package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.*;
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

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class RecepcionMercaderiaService extends CrudService<RecepcionMercaderia, RecepcionMercaderiaRepository, Long> {
    
    private final RecepcionMercaderiaRepository repository;
    
    @Autowired
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
     * Asocia notas de recepción a una recepción de mercadería
     */
    @Transactional
    public void asociarNotasRecepcion(Long recepcionId, List<Long> notaRecepcionIds) {
        RecepcionMercaderia recepcion = findById(recepcionId).orElseThrow(
            () -> new IllegalArgumentException("Recepción no encontrada: " + recepcionId)
        );
        
        for (Long notaId : notaRecepcionIds) {
            recepcionMercaderiaNotaService.asociarNotaARecepcion(recepcion, notaId);
        }
    }

    /**
     * Finaliza una recepción de mercadería - FUNCIÓN CRÍTICA
     * Genera movimientos de stock y actualiza costos para todos los ítems
     */
    @Transactional
    public RecepcionMercaderia finalizarRecepcion(Long recepcionId) {
        RecepcionMercaderia recepcion = findById(recepcionId).orElseThrow(
            () -> new IllegalArgumentException("Recepción no encontrada: " + recepcionId)
        );
        
        if (recepcion.getEstado() == RecepcionMercaderiaEstado.FINALIZADA) {
            throw new IllegalStateException("La recepción ya está finalizada");
        }
        
        // Obtener todos los ítems de la recepción
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemService
            .findByRecepcionMercaderiaId(recepcionId);
        
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
        
        // Actualizar las etapas del proceso para todos los pedidos relacionados
        actualizarEtapasProceso(recepcion);
        
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
     */
    private void generarMovimientoStock(RecepcionMercaderiaItem item, Double costoUnitario, 
                                      RecepcionMercaderia recepcion) {
        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setProducto(item.getProducto());
        movimiento.setSucursalId(item.getSucursalEntrega().getId());
        movimiento.setCantidad(item.getCantidadRecibida()); // MovimientoStock.cantidad is Double
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
        
        // Use existing method to find last cost for product
        // TODO: Need to create a method to find by producto and sucursal, or modify existing logic
        CostoPorProducto costoExistente = costoPorProductoService.findLastByProductoId(item.getProducto().getId());
        
        CostoPorProducto costo;
        if (costoExistente != null && costoExistente.getSucursal().getId().equals(item.getSucursalEntrega().getId())) {
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
        costo.setUsuario(recepcion.getUsuario());
        
        costoPorProductoService.save(costo);
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
     * Busca recepciones con filtros
     */
    public Page<RecepcionMercaderia> findByFilters(Long proveedorId, Long sucursalId, 
                                                  RecepcionMercaderiaEstado estado,
                                                  LocalDateTime fechaInicio, LocalDateTime fechaFin,
                                                  Pageable pageable) {
        return repository.findByFilters(proveedorId, sucursalId, estado, fechaInicio, fechaFin, pageable);
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
} 