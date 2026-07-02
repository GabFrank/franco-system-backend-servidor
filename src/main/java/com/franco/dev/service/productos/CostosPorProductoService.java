package com.franco.dev.service.productos;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.productos.builder.CostoMedioCalculator;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
public class CostosPorProductoService extends CrudService<CostoPorProducto, CostosPorProductoRepository, Long> {

    @Autowired
    private final CostosPorProductoRepository repository;

    @Autowired
    private MovimientoStockService movimientoStockService;
    @Autowired
    private com.franco.dev.service.configuraciones.ModificacionService modificacionService;

    @Override
    public CostosPorProductoRepository getRepository() {
        return repository;
    }

    public CostoPorProducto findLastByProductoId(Long prdoId) {
        List<CostoPorProducto> c = repository.findLastByProductoId(prdoId, PageRequest.of(0, 1));
        return c.size() > 0 ? c.get(0) : null;
    }

    public Page<CostoPorProducto> findByProductoId(Long id, Pageable page) {
        return repository.findByProductoId(id, page);
    }

    public CostoPorProducto findByMovimientoStockId(Long id) {
        return repository.findByMovimientoStockId(id);
    }

    @Override
    public CostoPorProducto save(CostoPorProducto entity) {
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());

        CostoPorProducto entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            java.util.Optional<CostoPorProducto> costoOpt = repository.findById(entity.getId());
            if (costoOpt != null && costoOpt.isPresent()) {
                CostoPorProducto original = costoOpt.get();
                entidadAnterior = new CostoPorProducto();
                entidadAnterior.setId(original.getId());
                entidadAnterior.setCostoMedio(original.getCostoMedio());
                entidadAnterior.setUltimoPrecioCompra(original.getUltimoPrecioCompra());
                entidadAnterior.setUltimoPrecioVenta(original.getUltimoPrecioVenta());
                entidadAnterior.setExistencia(original.getExistencia());
                entidadAnterior.setCotizacion(original.getCotizacion());
                entidadAnterior.setCreadoEn(original.getCreadoEn());
                entidadAnterior.setProducto(original.getProducto());
                entidadAnterior.setSucursal(original.getSucursal());
                entidadAnterior.setMoneda(original.getMoneda());
                entidadAnterior.setMovimientoStock(original.getMovimientoStock());
                entidadAnterior.setUsuario(original.getUsuario());
            }
        }

        CostoPorProducto e = super.save(entity);
        repository.flush();

        try {
            if (esNuevo) {
                modificacionService.registrarInsercion(e, "COSTO_POR_PRODUCTO", "productos", "costo_por_producto");
            } else if (entidadAnterior != null) {
                modificacionService.registrarActualizacion(entidadAnterior, e, "COSTO_POR_PRODUCTO", "productos",
                        "costo_por_producto");
            }
        } catch (Exception ex) {
        }

        return e;
    }

    @Override
    @javax.transaction.Transactional
    public Boolean deleteById(Long id) {
        try {
            CostoPorProducto entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                try {
                    modificacionService.registrarEliminacion(entidad, "COSTO_POR_PRODUCTO", "productos",
                            "costo_por_producto");
                } catch (Exception ex) {
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Nombre de la pseudo-sucursal usada como cuenta de "clearing" para inyectar compras
     * cuando el módulo de compras falla (transferencias desde COMPRAS). No es inventario real,
     * por eso se excluye del stock al ponderar el costo medio.
     */
    public static final String SUCURSAL_COMPRAS = "COMPRAS";

    /**
     * Aplica el costo de una compra real (recepción de mercadería) al producto.
     *
     * - costoMedio: promedio ponderado GLOBAL en Gs (moneda base), usando el inventario REAL
     *   (todas las sucursales EXCEPTO la pseudo-sucursal COMPRAS). Solo se pondera si hay base válida:
     *   stock anterior > 0, costo medio anterior > 0 y costo de la compra > 0. En cualquier otro caso
     *   (stock negativo o cero, sin costo previo válido) NO hay forma de ponderar y el costo medio se
     *   resetea al costo de esta compra — que es lo contable-correcto.
     * - ultimoPrecioCompra: precio de la compra en moneda original (sin convertir).
     * - Dedup: si el costo resultante es idéntico al último registro (mismo costoMedio, ultimoPrecioCompra
     *   y moneda), NO se inserta una fila nueva. Evita inflar la tabla con compras al mismo precio.
     *
     * @return el CostoPorProducto resultante (nuevo, o el último existente si no hubo cambio);
     *         null si la compra no tenía un costo válido (no aplica a bonificaciones).
     */
    @org.springframework.transaction.annotation.Transactional
    public CostoPorProducto aplicarCostoCompra(Producto producto, Double cantidadEntrada, Double costoUnitario,
                                               Moneda moneda, Double cotizacion, Sucursal sucursal,
                                               Usuario usuario, LocalDateTime fecha) {
        if (producto == null || producto.getId() == null) return null;
        if (costoUnitario == null || cantidadEntrada == null || cantidadEntrada <= 0) return null;

        double cotiz = (cotizacion != null) ? cotizacion : 1.0;
        // Costo de la compra normalizado a Gs para que el promedio ponderado sea correcto al mezclar monedas.
        double costoEnGs = CostoMedioCalculator.aGuaranies(costoUnitario, cotiz);
        if (costoEnGs <= 0) return null; // compra sin costo válido (data inválida): no se toca el costo

        CostoPorProducto costoAnterior = findLastByProductoId(producto.getId());

        // Inventario REAL del producto (excluye la pseudo-sucursal COMPRAS). El movimiento de entrada de
        // esta compra ya fue guardado antes, por lo que stockReal ya lo incluye.
        Double stockReal = movimientoStockService.stockByProductoIdExcluyendoNombresSucursal(
                producto.getId(), Arrays.asList(SUCURSAL_COMPRAS));
        if (stockReal == null) stockReal = 0.0;

        Double costoMedioAnterior = (costoAnterior != null) ? costoAnterior.getCostoMedio() : null;
        double nuevoCostoMedio = CostoMedioCalculator.calcular(stockReal, cantidadEntrada, costoMedioAnterior,
                costoEnGs);

        return guardarSiCambia(producto, nuevoCostoMedio, costoUnitario, moneda, cotiz, sucursal, usuario, fecha,
                costoAnterior);
    }

    /**
     * Registra el costo de una compra cargada manualmente vía transferencia desde la sucursal COMPRAS
     * (vía alternativa temporal mientras madura el módulo de compras).
     *
     * Esta vía no tiene una cantidad/stock confiable en el momento del cálculo (la transferencia avanza
     * por etapas), por lo que NO pondera: fija el costo medio al precio de la compra (en Gs). Igual aplica
     * el dedup para no inflar la tabla cuando se carga al mismo precio.
     *
     * @return el CostoPorProducto resultante (nuevo, o el último existente si no hubo cambio); null si inválido.
     */
    @org.springframework.transaction.annotation.Transactional
    public CostoPorProducto registrarCostoCompraManual(Producto producto, Double precioCosto,
                                                       Moneda moneda, Sucursal sucursal, Usuario usuario,
                                                       LocalDateTime fecha) {
        if (producto == null || producto.getId() == null) return null;
        if (precioCosto == null || precioCosto <= 0) return null;
        CostoPorProducto costoAnterior = findLastByProductoId(producto.getId());
        // precioCosto ya viene en Gs (las transferencias operan en GUARANI)
        return guardarSiCambia(producto, precioCosto, precioCosto, moneda, 1.0, sucursal, usuario, fecha,
                costoAnterior);
    }

    /**
     * Inserta un nuevo registro de costo SOLO si cambió respecto al último (dedup).
     * Compara costoMedio, ultimoPrecioCompra (con tolerancia, por ruido de punto flotante) y moneda.
     */
    private CostoPorProducto guardarSiCambia(Producto producto, double costoMedio, double ultimoPrecioCompra,
                                             Moneda moneda, double cotizacion, Sucursal sucursal,
                                             Usuario usuario, LocalDateTime fecha, CostoPorProducto costoAnterior) {
        Long monedaIdAnterior = (costoAnterior != null && costoAnterior.getMoneda() != null)
                ? costoAnterior.getMoneda().getId() : null;
        Long monedaIdNuevo = (moneda != null) ? moneda.getId() : null;
        boolean cambio = costoAnterior == null || CostoMedioCalculator.cambio(
                costoAnterior.getCostoMedio(), costoAnterior.getUltimoPrecioCompra(), monedaIdAnterior,
                costoMedio, ultimoPrecioCompra, monedaIdNuevo);
        if (!cambio) {
            // El costo no cambió: no se inserta fila nueva.
            return costoAnterior;
        }
        CostoPorProducto nuevo = new CostoPorProducto();
        nuevo.setProducto(producto);
        nuevo.setSucursal(sucursal); // informativo: recepción/transferencia que originó el registro
        nuevo.setCostoMedio(costoMedio);
        nuevo.setUltimoPrecioCompra(ultimoPrecioCompra);
        nuevo.setMoneda(moneda);
        nuevo.setCotizacion(cotizacion);
        if (usuario != null) nuevo.setUsuario(usuario);
        nuevo.setCreadoEn(fecha != null ? fecha : LocalDateTime.now());
        return save(nuevo);
    }

    public Double calcularCostoMedio(Long productoId, Double cantidad, Double precioCompra) {
        CostoPorProducto costo = findLastByProductoId(productoId);
        if (costo != null) {
            Double ultimoCostoMedio = costo.getCostoMedio();
            Double stockActual = movimientoStockService.stockByProductoId(productoId);
            if (ultimoCostoMedio != null && stockActual != null && stockActual > 0) {
                return ((ultimoCostoMedio * stockActual) + (cantidad * precioCompra)) / (stockActual + cantidad);
            } else {
                return precioCompra;
            }
        } else {
            return precioCompra;
        }
    }
}
