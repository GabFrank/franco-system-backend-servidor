package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import com.franco.dev.graphql.operaciones.dto.ProductoCompraPorPeriodo;
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

        String sql = "SELECT p.id, p.descripcion, SUM(vi.cantidad) AS cantidad, "
                + "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) AS total_monto "
                + "FROM operaciones.venta_item vi "
                + "JOIN productos.producto p ON vi.producto_id = p.id "
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
        String sql = "SELECT CAST(vi.creado_en AS DATE) as periodo, SUM(vi.cantidad) as cantidad, " +
                "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) as total_monto " +
                "FROM operaciones.venta_item vi " +
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
        String sql = "SELECT TO_CHAR(vi.creado_en, 'YYYY-MM') as periodo, SUM(vi.cantidad) as cantidad, " +
                "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) as total_monto " +
                "FROM operaciones.venta_item vi " +
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

    private List<ProductoVentaPorPeriodo> mapearPeriodos(List<Object[]> resultados) {
        List<ProductoVentaPorPeriodo> periodos = new ArrayList<>();
        for (Object[] fila : resultados) {
            String periodo = fila[0] != null ? fila[0].toString() : "";
            Double cantidad = fila[1] != null ? ((Number) fila[1]).doubleValue() : 0.0;
            Double totalMonto = fila[2] != null ? ((Number) fila[2]).doubleValue() : 0.0;
            periodos.add(new ProductoVentaPorPeriodo(periodo, cantidad, totalMonto));
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
            dto.setPorcentaje(porcentaje);
            dto.setCantidadEntrada(cantidadEntrada);
            dto.setCantidadVentaMovimiento(cantidadVentaMovimiento);
            dto.setIndiceRotacion(indiceRotacion);
            estadisticas.add(dto);
        }

        return estadisticas;
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