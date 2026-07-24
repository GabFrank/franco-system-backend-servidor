package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import com.franco.dev.graphql.operaciones.dto.ProductoCompraPorPeriodo;
import com.franco.dev.graphql.operaciones.dto.ProductoCostoPorPeriodo;
import com.franco.dev.graphql.operaciones.dto.EvolucionCostoResponse;
import com.franco.dev.graphql.operaciones.dto.EvolucionCostoResumen;
import com.franco.dev.graphql.operaciones.dto.RankingInflacionItem;
import com.franco.dev.graphql.operaciones.dto.ProductoVentaPorPeriodo;
import com.franco.dev.repository.operaciones.VentaItemRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.CostosPorProductoService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class VentaItemService extends CrudService<VentaItem, VentaItemRepository, EmbebedPrimaryKey> {
    private final VentaItemRepository repository;
    private final EntityManager em;

    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Override
    public VentaItemRepository getRepository() {
        return repository;
    }

    @Autowired
    MovimientoStockService movimientoStockService;

    public List<VentaItem> findByVentaId(Long id, Long sucId) {
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    @Override
    public VentaItem save(VentaItem entity) {
        if (entity.getPrecioCosto() == null) {
            CostoPorProducto costoPorProducto = costosPorProductoService
                    .findLastByProductoId(entity.getProducto().getId());
            if (costoPorProducto != null) {
                entity.setPrecioCosto(costoPorProducto.getUltimoPrecioCompra());
            }
        }
        VentaItem e = super.save(entity);
        return e;
    }

    public VentaItem findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    public Boolean deleteByIdAndSucursalId(Long id, Long sucId) {
        return repository.deleteByIdAndSucursalId(id, sucId);
    }

    /**
     * Entradas de stock para análisis: recepciones (COMPRA) y transferencias desde
     * sucursal COMPRAS hacia cualquier destino (movimiento positivo en destino).
     */
    private String sqlCondicionEntradasStock() {
        return "ms.estado = true AND ms.cantidad > 0 AND ("
                + "ms.tipo_movimiento = 'COMPRA' OR ("
                + "ms.tipo_movimiento = 'TRANSFERENCIA' AND EXISTS ("
                + "  SELECT 1 FROM operaciones.transferencia_item ti "
                + "  JOIN operaciones.transferencia t ON t.id = ti.transferencia_id "
                + "  WHERE ti.id = ms.referencia "
                + "  AND t.sucursal_origen_id IN ("
                + "    SELECT s.id FROM empresarial.sucursal s WHERE UPPER(s.nombre) LIKE '%COMPRAS%'"
                + "  )"
                + ")))";
    }

    /**
     * Obtiene estadísticas de productos más vendidos con filtros usando
     * CriteriaBuilder
     */
    public List<ProductoVendidoEstadistica> obtenerProductosMasVendidos(LocalDateTime inicio, LocalDateTime fin,
            Integer limit, Long sucursalId, Long familiaId, Long subfamiliaId, Boolean ascendente, Long productoId,
            List<Long> productoIds) {
        int limite = limit != null && limit > 0 ? limit : 10;
        boolean ordenAsc = Boolean.TRUE.equals(ascendente);

        List<Object[]> topProductos = consultarTopProductosVendidos(
                inicio, fin, limite, sucursalId, familiaId, subfamiliaId, ordenAsc, productoId, productoIds);
        if (topProductos.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> topProductoIds = new ArrayList<>(topProductos.size());
        for (Object[] fila : topProductos) {
            if (fila[0] != null) {
                topProductoIds.add(((Number) fila[0]).longValue());
            }
        }
        if (topProductoIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Double> entradas = consultarCantidadEntradaStock(
                topProductoIds, inicio, fin, sucursalId);
        Map<Long, Double> ventasMovimiento = consultarCantidadVentaMovimientoStock(
                topProductoIds, inicio, fin, sucursalId);

        List<Object[]> resultados = new ArrayList<>(topProductos.size());
        for (Object[] fila : topProductos) {
            Long id = fila[0] != null ? ((Number) fila[0]).longValue() : null;
            resultados.add(new Object[] {
                    fila[0],
                    fila[1],
                    fila[2],
                    fila[3],
                    id != null ? entradas.getOrDefault(id, 0.0) : 0.0,
                    id != null ? ventasMovimiento.getOrDefault(id, 0.0) : 0.0
            });
        }

        return transformarResultadosAEstadisticas(resultados);
    }

    private List<Object[]> consultarTopProductosVendidos(
            LocalDateTime inicio,
            LocalDateTime fin,
            int limite,
            Long sucursalId,
            Long familiaId,
            Long subfamiliaId,
            boolean ordenAsc,
            Long productoId,
            List<Long> productoIds) {
        String orden = ordenAsc ? "ASC" : "DESC";
        String filtrosVi = construirFiltrosVentaItem(
                inicio, fin, sucursalId, familiaId, subfamiliaId, productoId, productoIds);

        String sql = "SELECT p.id, p.descripcion, SUM(vi.cantidad * pre.cantidad) AS cantidad, "
                + "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) AS total_monto "
                + "FROM operaciones.venta_item vi "
                + "JOIN productos.producto p ON vi.producto_id = p.id "
                + "JOIN productos.presentacion pre ON pre.id = vi.presentacion_id "
                + "JOIN operaciones.venta v ON vi.venta_id = v.id AND vi.sucursal_id = v.sucursal_id "
                + "LEFT JOIN productos.subfamilia sf ON p.sub_familia_id = sf.id "
                + "WHERE v.estado = 'CONCLUIDA' AND vi.activo = true "
                + filtrosVi
                + "GROUP BY p.id, p.descripcion "
                + "ORDER BY cantidad " + orden;

        javax.persistence.Query query = em.createNativeQuery(sql);
        vincularFiltrosVentaItem(
                query, inicio, fin, sucursalId, familiaId, subfamiliaId, productoId, productoIds);
        query.setMaxResults(limite);

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();
        return resultados;
    }

    private Map<Long, Double> consultarCantidadEntradaStock(
            List<Long> topProductoIds,
            LocalDateTime inicio,
            LocalDateTime fin,
            Long sucursalId) {
        String filtroFechaMs = construirFiltroFechaMovimientoStock(inicio, fin);
        String filtroSucursalMs = construirFiltroSucursalMovimientoStock(sucursalId);

        String sql = "SELECT ms.producto_id, SUM(ms.cantidad) AS cantidad_entrada "
                + "FROM operaciones.movimiento_stock ms "
                + "WHERE " + sqlCondicionEntradasStock() + " "
                + filtroFechaMs
                + filtroSucursalMs
                + "AND ms.producto_id IN :topProductoIds "
                + "GROUP BY ms.producto_id";

        javax.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("topProductoIds", topProductoIds);
        vincularFiltrosMovimientoStock(query, inicio, fin, sucursalId);

        return mapearTotalesPorProducto(query.getResultList());
    }

    private Map<Long, Double> consultarCantidadVentaMovimientoStock(
            List<Long> topProductoIds,
            LocalDateTime inicio,
            LocalDateTime fin,
            Long sucursalId) {
        String filtroFechaMs = construirFiltroFechaMovimientoStock(inicio, fin);
        String filtroSucursalMs = construirFiltroSucursalMovimientoStock(sucursalId);

        String sql = "SELECT ms.producto_id, SUM(ABS(ms.cantidad)) AS cantidad_venta_mov "
                + "FROM operaciones.movimiento_stock ms "
                + "WHERE ms.estado = true AND ms.tipo_movimiento = 'VENTA' AND ms.cantidad < 0 "
                + filtroFechaMs
                + filtroSucursalMs
                + "AND ms.producto_id IN :topProductoIds "
                + "GROUP BY ms.producto_id";

        javax.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("topProductoIds", topProductoIds);
        vincularFiltrosMovimientoStock(query, inicio, fin, sucursalId);

        return mapearTotalesPorProducto(query.getResultList());
    }

    private String construirFiltrosVentaItem(
            LocalDateTime inicio,
            LocalDateTime fin,
            Long sucursalId,
            Long familiaId,
            Long subfamiliaId,
            Long productoId,
            List<Long> productoIds) {
        StringBuilder filtrosVi = new StringBuilder();
        if (inicio != null) {
            filtrosVi.append("AND vi.creado_en >= :inicio ");
        }
        if (fin != null) {
            filtrosVi.append("AND vi.creado_en <= :fin ");
        }
        if (sucursalId != null && sucursalId > 0) {
            filtrosVi.append("AND vi.sucursal_id = :sucursalId ");
        }
        if (subfamiliaId != null && subfamiliaId > 0) {
            filtrosVi.append("AND p.sub_familia_id = :subfamiliaId ");
        } else if (familiaId != null && familiaId > 0) {
            filtrosVi.append("AND sf.familia_id = :familiaId ");
        }
        if (productoIds != null && !productoIds.isEmpty()) {
            filtrosVi.append("AND vi.producto_id IN :productoIds ");
        } else if (productoId != null && productoId > 0) {
            filtrosVi.append("AND vi.producto_id = :productoId ");
        }
        return filtrosVi.toString();
    }

    private String construirFiltroFechaMovimientoStock(LocalDateTime inicio, LocalDateTime fin) {
        String filtroFechaMs = "";
        if (inicio != null) {
            filtroFechaMs += "AND ms.creado_en >= :inicio ";
        }
        if (fin != null) {
            filtroFechaMs += "AND ms.creado_en <= :fin ";
        }
        return filtroFechaMs;
    }

    private String construirFiltroSucursalMovimientoStock(Long sucursalId) {
        return (sucursalId != null && sucursalId > 0) ? "AND ms.sucursal_id = :sucursalId " : "";
    }

    private void vincularFiltrosVentaItem(
            javax.persistence.Query query,
            LocalDateTime inicio,
            LocalDateTime fin,
            Long sucursalId,
            Long familiaId,
            Long subfamiliaId,
            Long productoId,
            List<Long> productoIds) {
        if (inicio != null) {
            query.setParameter("inicio", inicio);
        }
        if (fin != null) {
            query.setParameter("fin", fin);
        }
        if (sucursalId != null && sucursalId > 0) {
            query.setParameter("sucursalId", sucursalId);
        }
        if (subfamiliaId != null && subfamiliaId > 0) {
            query.setParameter("subfamiliaId", subfamiliaId);
        } else if (familiaId != null && familiaId > 0) {
            query.setParameter("familiaId", familiaId);
        }
        if (productoIds != null && !productoIds.isEmpty()) {
            query.setParameter("productoIds", productoIds);
        } else if (productoId != null && productoId > 0) {
            query.setParameter("productoId", productoId);
        }
    }

    private void vincularFiltrosMovimientoStock(
            javax.persistence.Query query,
            LocalDateTime inicio,
            LocalDateTime fin,
            Long sucursalId) {
        if (inicio != null) {
            query.setParameter("inicio", inicio);
        }
        if (fin != null) {
            query.setParameter("fin", fin);
        }
        if (sucursalId != null && sucursalId > 0) {
            query.setParameter("sucursalId", sucursalId);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Double> mapearTotalesPorProducto(List<Object[]> filas) {
        Map<Long, Double> totales = new HashMap<>();
        if (filas == null) {
            return totales;
        }
        for (Object[] fila : filas) {
            if (fila[0] == null) {
                continue;
            }
            Long productoId = ((Number) fila[0]).longValue();
            Double total = fila[1] != null ? ((Number) fila[1]).doubleValue() : 0.0;
            totales.put(productoId, total);
        }
        return totales;
    }

    public List<ProductoVentaPorPeriodo> obtenerVentasProductoPorDia(LocalDateTime inicio, LocalDateTime fin,
            Long productoId, Long sucursalId) {
        String sql = "SELECT CAST(vi.creado_en AS DATE) as periodo, SUM(vi.cantidad * pre.cantidad) as cantidad, " +
                "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) as total_monto " +
                "FROM operaciones.venta_item vi " +
                "JOIN productos.presentacion pre ON pre.id = vi.presentacion_id " +
                "JOIN operaciones.venta v ON vi.venta_id = v.id AND vi.sucursal_id = v.sucursal_id " +
                "WHERE v.estado = 'CONCLUIDA' AND vi.activo = true AND vi.producto_id = :productoId ";

        if (inicio != null) sql += "AND vi.creado_en >= :inicio ";
        if (fin != null) sql += "AND vi.creado_en < :fin ";
        if (sucursalId != null && sucursalId > 0) sql += "AND vi.sucursal_id = :sucursalId ";

        sql += "GROUP BY CAST(vi.creado_en AS DATE) ORDER BY periodo ASC";

        javax.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("productoId", productoId);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fin != null) query.setParameter("fin", fin);
        if (sucursalId != null && sucursalId > 0) query.setParameter("sucursalId", sucursalId);

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();
        return mapearPeriodos(resultados);
    }

    public List<ProductoVentaPorPeriodo> obtenerVentasProductoPorMes(LocalDateTime inicio, LocalDateTime fin,
            Long productoId, Long sucursalId) {
        String sql = "SELECT TO_CHAR(vi.creado_en, 'YYYY-MM') as periodo, SUM(vi.cantidad * pre.cantidad) as cantidad, " +
                "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) as total_monto " +
                "FROM operaciones.venta_item vi " +
                "JOIN productos.presentacion pre ON pre.id = vi.presentacion_id " +
                "JOIN operaciones.venta v ON vi.venta_id = v.id AND vi.sucursal_id = v.sucursal_id " +
                "WHERE v.estado = 'CONCLUIDA' AND vi.activo = true AND vi.producto_id = :productoId ";

        if (inicio != null) sql += "AND vi.creado_en >= :inicio ";
        if (fin != null) sql += "AND vi.creado_en < :fin ";
        if (sucursalId != null && sucursalId > 0) sql += "AND vi.sucursal_id = :sucursalId ";

        sql += "GROUP BY TO_CHAR(vi.creado_en, 'YYYY-MM') ORDER BY periodo ASC";

        javax.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("productoId", productoId);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fin != null) query.setParameter("fin", fin);
        if (sucursalId != null && sucursalId > 0) query.setParameter("sucursalId", sucursalId);

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();
        return mapearPeriodos(resultados);
    }

    public List<ProductoCompraPorPeriodo> obtenerComprasProductoPorDia(LocalDateTime inicio, LocalDateTime fin,
            Long productoId, Long sucursalId) {
        String sql = "SELECT CAST(ms.creado_en AS DATE) as periodo, SUM(ms.cantidad) as cantidad, " +
                "COUNT(*) as cantidad_compras " +
                "FROM operaciones.movimiento_stock ms " +
                "WHERE " + sqlCondicionEntradasStock() + " " +
                "AND ms.producto_id = :productoId ";

        if (inicio != null) sql += "AND ms.creado_en >= :inicio ";
        if (fin != null) sql += "AND ms.creado_en < :fin ";
        if (sucursalId != null && sucursalId > 0) sql += "AND ms.sucursal_id = :sucursalId ";

        sql += "GROUP BY CAST(ms.creado_en AS DATE) ORDER BY periodo ASC";

        javax.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("productoId", productoId);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fin != null) query.setParameter("fin", fin);
        if (sucursalId != null && sucursalId > 0) query.setParameter("sucursalId", sucursalId);

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();
        return mapearComprasPeriodos(resultados);
    }

    public List<ProductoCompraPorPeriodo> obtenerComprasProductoPorMes(LocalDateTime inicio, LocalDateTime fin,
            Long productoId, Long sucursalId) {
        String sql = "SELECT TO_CHAR(ms.creado_en, 'YYYY-MM') as periodo, SUM(ms.cantidad) as cantidad, " +
                "COUNT(*) as cantidad_compras " +
                "FROM operaciones.movimiento_stock ms " +
                "WHERE " + sqlCondicionEntradasStock() + " " +
                "AND ms.producto_id = :productoId ";

        if (inicio != null) sql += "AND ms.creado_en >= :inicio ";
        if (fin != null) sql += "AND ms.creado_en < :fin ";
        if (sucursalId != null && sucursalId > 0) sql += "AND ms.sucursal_id = :sucursalId ";

        sql += "GROUP BY TO_CHAR(ms.creado_en, 'YYYY-MM') ORDER BY periodo ASC";

        javax.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("productoId", productoId);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fin != null) query.setParameter("fin", fin);
        if (sucursalId != null && sucursalId > 0) query.setParameter("sucursalId", sucursalId);

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();
        return mapearComprasPeriodos(resultados);
    }

    private List<ProductoCompraPorPeriodo> mapearComprasPeriodos(List<Object[]> resultados) {
        List<ProductoCompraPorPeriodo> periodos = new ArrayList<>();
        for (Object[] fila : resultados) {
            String periodo = fila[0] != null ? fila[0].toString() : "";
            Double cantidad = fila[1] != null ? ((Number) fila[1]).doubleValue() : 0.0;
            Integer cantidadCompras = fila[2] != null ? ((Number) fila[2]).intValue() : 0;
            periodos.add(new ProductoCompraPorPeriodo(periodo, cantidad, cantidadCompras));
        }
        return periodos;
    }

    /**
     * Evolucion del costo unitario de compra de un producto: serie por periodo + resumen calculado.
     * Fuente: operaciones.compra_item (precio_unitario real de factura) unido a compra.
     * Solo compras ACTIVAS (interfieren en stock), con precio > 0 y excluyendo bonificaciones
     * (items regalados con precio 0 que distorsionarian el promedio).
     *
     * @param agrupacion "dia" agrupa por fecha; cualquier otro valor (por defecto) agrupa por mes.
     */
    public EvolucionCostoResponse obtenerEvolucionCostoProducto(LocalDateTime inicio, LocalDateTime fin,
            Long productoId, Long sucursalId, String agrupacion) {
        // El costo de compra se lleva de forma global (no por sucursal), por eso sucursalId no se usa.
        List<ProductoCostoPorPeriodo> periodos =
                obtenerCostoCompraProductoPorPeriodo(inicio, fin, productoId, agrupacion);
        return new EvolucionCostoResponse(periodos, calcularResumenCosto(periodos));
    }

    /** Calcula los indicadores agregados de la evolucion de costo (variacion, promedio ponderado, pico). */
    private EvolucionCostoResumen calcularResumenCosto(List<ProductoCostoPorPeriodo> periodos) {
        List<ProductoCostoPorPeriodo> conDato = new ArrayList<>();
        for (ProductoCostoPorPeriodo p : periodos) {
            if (p.getCostoPromedio() != null && p.getCostoPromedio() > 0) {
                conDato.add(p);
            }
        }

        if (conDato.isEmpty()) {
            return new EvolucionCostoResumen(0.0, 0.0, 0.0, 0.0, "-", 0, false);
        }

        double costoInicial = conDato.get(0).getCostoPromedio();
        double costoFinal = conDato.get(conDato.size() - 1).getCostoPromedio();
        double variacion = costoInicial > 0 ? ((costoFinal - costoInicial) / costoInicial) * 100.0 : 0.0;

        double sumaPonderada = 0.0;
        double sumaCantidad = 0.0;
        String periodoPico = conDato.get(0).getPeriodo();
        double costoPico = -1.0;
        int totalCompras = 0;
        for (ProductoCostoPorPeriodo p : conDato) {
            double cantidad = p.getCantidad() != null ? p.getCantidad() : 0.0;
            sumaPonderada += p.getCostoPromedio() * cantidad;
            sumaCantidad += cantidad;
            if (p.getCostoPromedio() > costoPico) {
                costoPico = p.getCostoPromedio();
                periodoPico = p.getPeriodo();
            }
        }
        for (ProductoCostoPorPeriodo p : periodos) {
            totalCompras += p.getCantidadCompras() != null ? p.getCantidadCompras() : 0;
        }
        double promedioPonderado = sumaCantidad > 0 ? sumaPonderada / sumaCantidad : costoFinal;

        return new EvolucionCostoResumen(costoInicial, costoFinal, variacion, promedioPonderado,
                periodoPico, totalCompras, true);
    }

    /**
     * Serie de costo de compra por periodo. El costo real se registra de forma global (no por sucursal)
     * en productos.costo_por_producto, tabla que guarda el ultimo_precio_compra cada vez que se
     * actualiza el costo de un producto. Las cantidades compradas y el numero de compras se toman de
     * los movimientos de stock tipo COMPRA. El precio se normaliza a guaranies multiplicando por la
     * cotizacion (1 para moneda local).
     */
    private List<ProductoCostoPorPeriodo> obtenerCostoCompraProductoPorPeriodo(LocalDateTime inicio, LocalDateTime fin,
            Long productoId, String agrupacion) {
        boolean porDia = "dia".equalsIgnoreCase(agrupacion);
        String periodoCosto = porDia ? "CAST(cpp.creado_en AS DATE)" : "TO_CHAR(cpp.creado_en, 'YYYY-MM')";
        String periodoCompra = porDia ? "CAST(ms.creado_en AS DATE)" : "TO_CHAR(ms.creado_en, 'YYYY-MM')";
        String costoGs = "cpp.ultimo_precio_compra * COALESCE(NULLIF(cpp.cotizacion, 0), 1)";

        StringBuilder filtroCosto = new StringBuilder();
        StringBuilder filtroCompra = new StringBuilder();
        if (inicio != null) {
            filtroCosto.append("AND cpp.creado_en >= :inicio ");
            filtroCompra.append("AND ms.creado_en >= :inicio ");
        }
        if (fin != null) {
            filtroCosto.append("AND cpp.creado_en < :fin ");
            filtroCompra.append("AND ms.creado_en < :fin ");
        }

        String sql = "WITH costos AS ( " +
                "  SELECT " + periodoCosto + " AS periodo, " +
                "         AVG(" + costoGs + ") AS costo_promedio, " +
                "         MIN(" + costoGs + ") AS costo_minimo, " +
                "         MAX(" + costoGs + ") AS costo_maximo " +
                "  FROM productos.costo_por_producto cpp " +
                "  WHERE cpp.producto_id = :productoId AND cpp.ultimo_precio_compra > 0 " +
                filtroCosto.toString() +
                "  GROUP BY " + periodoCosto + " " +
                "), compras AS ( " +
                // Se considera compra tanto un movimiento COMPRA como una TRANSFERENCIA cuyo origen es
                // la sucursal COMPRAS (asi funcionaba el abastecimiento antes del modulo de compras).
                "  SELECT " + periodoCompra + " AS periodo, " +
                "         SUM(ms.cantidad) AS cantidad, " +
                "         COUNT(*) AS cantidad_compras " +
                "  FROM operaciones.movimiento_stock ms " +
                "  WHERE ms.producto_id = :productoId AND " + sqlCondicionEntradasStock() + " " +
                filtroCompra.toString() +
                "  GROUP BY " + periodoCompra + " " +
                ") " +
                "SELECT c.periodo, c.costo_promedio, c.costo_minimo, c.costo_maximo, " +
                "       COALESCE(cm.cantidad, 0) AS cantidad, COALESCE(cm.cantidad_compras, 0) AS cantidad_compras " +
                "FROM costos c LEFT JOIN compras cm ON cm.periodo = c.periodo " +
                "ORDER BY c.periodo ASC";

        javax.persistence.Query query = em.createNativeQuery(sql);
        query.setParameter("productoId", productoId);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fin != null) query.setParameter("fin", fin);

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();
        return mapearCostoPeriodos(resultados);
    }

    private List<ProductoCostoPorPeriodo> mapearCostoPeriodos(List<Object[]> resultados) {
        List<ProductoCostoPorPeriodo> periodos = new ArrayList<>();
        for (Object[] fila : resultados) {
            String periodo = fila[0] != null ? fila[0].toString() : "";
            Double costoPromedio = fila[1] != null ? ((Number) fila[1]).doubleValue() : 0.0;
            Double costoMinimo = fila[2] != null ? ((Number) fila[2]).doubleValue() : 0.0;
            Double costoMaximo = fila[3] != null ? ((Number) fila[3]).doubleValue() : 0.0;
            Double cantidad = fila[4] != null ? ((Number) fila[4]).doubleValue() : 0.0;
            Integer cantidadCompras = fila[5] != null ? ((Number) fila[5]).intValue() : 0;
            periodos.add(new ProductoCostoPorPeriodo(periodo, costoPromedio, costoMinimo, costoMaximo, cantidad,
                    cantidadCompras));
        }
        return periodos;
    }

    /**
     * Ranking de productos por variacion de costo de compra entre el primer y ultimo mes con compras
     * dentro del periodo. Sirve para detectar inflacion de proveedores de un vistazo.
     * Solo considera productos con al menos 2 meses distintos de compras (para que exista variacion).
     *
     * @param orden "baja" ordena por mayor reduccion; cualquier otro valor (default) por mayor suba.
     */
    public List<RankingInflacionItem> obtenerRankingInflacionCosto(LocalDateTime inicio, LocalDateTime fin,
            Long sucursalId, Long familiaId, Integer limit, String orden) {
        StringBuilder filtros = new StringBuilder();
        if (inicio != null) filtros.append("AND COALESCE(c.fecha, c.creado_en) >= :inicio ");
        if (fin != null) filtros.append("AND COALESCE(c.fecha, c.creado_en) < :fin ");
        if (sucursalId != null && sucursalId > 0) filtros.append("AND c.sucursal_id = :sucursalId ");
        if (familiaId != null && familiaId > 0) filtros.append("AND sf.familia_id = :familiaId ");

        String sql = "WITH costos_mes AS ( " +
                "  SELECT ci.producto_id AS producto_id, p.descripcion AS descripcion, " +
                "         TO_CHAR(COALESCE(c.fecha, c.creado_en), 'YYYY-MM') AS periodo, " +
                "         SUM(ci.precio_unitario * ci.cantidad) / NULLIF(SUM(ci.cantidad), 0) AS costo, " +
                "         COUNT(DISTINCT c.id) AS compras " +
                "  FROM operaciones.compra_item ci " +
                "  JOIN operaciones.compra c ON c.id = ci.compra_id " +
                "  JOIN productos.producto p ON p.id = ci.producto_id " +
                "  LEFT JOIN productos.subfamilia sf ON p.sub_familia_id = sf.id " +
                "  WHERE c.estado = 'ACTIVO' AND ci.precio_unitario > 0 " +
                "    AND (ci.bonificacion IS NULL OR ci.bonificacion = false) " +
                filtros.toString() +
                "  GROUP BY ci.producto_id, p.descripcion, TO_CHAR(COALESCE(c.fecha, c.creado_en), 'YYYY-MM') " +
                "), ranked AS ( " +
                "  SELECT producto_id, descripcion, costo, compras, " +
                "         ROW_NUMBER() OVER (PARTITION BY producto_id ORDER BY periodo ASC) AS rn_asc, " +
                "         ROW_NUMBER() OVER (PARTITION BY producto_id ORDER BY periodo DESC) AS rn_desc, " +
                "         COUNT(*) OVER (PARTITION BY producto_id) AS n_periodos " +
                "  FROM costos_mes " +
                ") " +
                "SELECT producto_id, MAX(descripcion) AS descripcion, " +
                "       MAX(CASE WHEN rn_asc = 1 THEN costo END) AS costo_inicial, " +
                "       MAX(CASE WHEN rn_desc = 1 THEN costo END) AS costo_final, " +
                "       MAX(n_periodos) AS periodos, SUM(compras) AS total_compras " +
                "FROM ranked GROUP BY producto_id HAVING MAX(n_periodos) >= 2";

        javax.persistence.Query query = em.createNativeQuery(sql);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fin != null) query.setParameter("fin", fin);
        if (sucursalId != null && sucursalId > 0) query.setParameter("sucursalId", sucursalId);
        if (familiaId != null && familiaId > 0) query.setParameter("familiaId", familiaId);

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();

        List<RankingInflacionItem> items = new ArrayList<>();
        for (Object[] fila : resultados) {
            Long productoId = fila[0] != null ? ((Number) fila[0]).longValue() : null;
            String descripcion = fila[1] != null ? fila[1].toString() : "";
            Double costoInicial = fila[2] != null ? ((Number) fila[2]).doubleValue() : 0.0;
            Double costoFinal = fila[3] != null ? ((Number) fila[3]).doubleValue() : 0.0;
            Integer periodos = fila[4] != null ? ((Number) fila[4]).intValue() : 0;
            Integer totalCompras = fila[5] != null ? ((Number) fila[5]).intValue() : 0;
            Double variacion = costoInicial > 0 ? ((costoFinal - costoInicial) / costoInicial) * 100.0 : 0.0;
            items.add(new RankingInflacionItem(productoId, descripcion, costoInicial, costoFinal, variacion,
                    periodos, totalCompras));
        }

        boolean ascendente = "baja".equalsIgnoreCase(orden);
        items.sort((a, b) -> ascendente
                ? Double.compare(a.getVariacionPorcentual(), b.getVariacionPorcentual())
                : Double.compare(b.getVariacionPorcentual(), a.getVariacionPorcentual()));

        int tope = (limit != null && limit > 0) ? limit : 20;
        return items.size() > tope ? new ArrayList<>(items.subList(0, tope)) : items;
    }

    private List<ProductoVentaPorPeriodo> mapearPeriodos(List<Object[]> resultados) {
        List<ProductoVentaPorPeriodo> periodos = new ArrayList<>();
        for (Object[] fila : resultados) {
            String periodo = fila[0] != null ? fila[0].toString() : "";
            Double cantidad = fila[1] != null ? ((Number) fila[1]).doubleValue() : 0.0;
            Double totalMonto = fila[2] != null ? ((Number) fila[2]).doubleValue() : 0.0;
            ProductoVentaPorPeriodo dto = new ProductoVentaPorPeriodo();
            dto.setPeriodo(periodo);
            dto.setCantidad(cantidad);
            dto.setTotalMonto(totalMonto);
            dto.setPrecioPromedio(calcularPrecioPromedio(totalMonto, cantidad));
            periodos.add(dto);
        }
        return periodos;
    }

    /**
     * Transforma los resultados de la query a objetos ProductoVendidoEstadistica
     */
    private List<ProductoVendidoEstadistica> transformarResultadosAEstadisticas(List<Object[]> resultados) {
        List<ProductoVendidoEstadistica> estadisticas = new ArrayList<>();
        Double montoTotalGeneral = 0.0;

        for (Object[] fila : resultados) {
            Double monto = fila[3] != null ? ((Number) fila[3]).doubleValue() : 0.0;
            montoTotalGeneral += monto;
        }

        for (Object[] fila : resultados) {
            Long productoId = fila[0] != null ? ((Number) fila[0]).longValue() : null;
            String descripcion = fila[1] != null ? fila[1].toString() : "";
            Double cantidad = fila[2] != null ? ((Number) fila[2]).doubleValue() : 0.0;
            Double totalMonto = fila[3] != null ? ((Number) fila[3]).doubleValue() : 0.0;
            Double cantidadEntrada = fila[4] != null ? ((Number) fila[4]).doubleValue() : 0.0;
            Double cantidadVentaMovimiento = fila[5] != null ? ((Number) fila[5]).doubleValue() : 0.0;

            Double porcentaje = 0.0;
            if (montoTotalGeneral > 0) {
                porcentaje = (totalMonto * 100.0) / montoTotalGeneral;
                porcentaje = Math.round(porcentaje * 100.0) / 100.0;
            }

            Double indiceRotacion = calcularIndiceRotacion(cantidadEntrada, cantidadVentaMovimiento);

            ProductoVendidoEstadistica dto = new ProductoVendidoEstadistica();
            dto.setProductoId(productoId);
            dto.setDescripcion(descripcion);
            dto.setCantidad(cantidad);
            dto.setTotalMonto(totalMonto);
            dto.setPrecioPromedio(calcularPrecioPromedio(totalMonto, cantidad));
            dto.setPorcentaje(porcentaje);
            dto.setCantidadEntrada(cantidadEntrada);
            dto.setCantidadVentaMovimiento(cantidadVentaMovimiento);
            dto.setIndiceRotacion(indiceRotacion);
            estadisticas.add(dto);
        }

        return estadisticas;
    }

    /**
     * Precio promedio por unidad base: dinero vendido / unidades vendidas.
     */
    private Double calcularPrecioPromedio(Double totalMonto, Double cantidad) {
        if (cantidad != null && cantidad > 0 && totalMonto != null) {
            return Math.round((totalMonto / cantidad) * 100.0) / 100.0;
        }
        return 0.0;
    }

    /**
     * Índice de rotación: unidades vendidas (mov. VENTA) / unidades que entraron (COMPRA + TRANSFERENCIA).
     * Sin entradas en el período pero con ventas: 1.0 (rota stock previo).
     */
    private Double calcularIndiceRotacion(Double cantidadEntrada, Double cantidadVentaMovimiento) {
        double entradas = cantidadEntrada != null ? cantidadEntrada : 0.0;
        double ventas = cantidadVentaMovimiento != null ? cantidadVentaMovimiento : 0.0;
        if (entradas > 0) {
            double indice = ventas / entradas;
            return Math.round(indice * 1000.0) / 1000.0;
        }
        if (ventas > 0) {
            return 1.0;
        }
        return 0.0;
    }
}