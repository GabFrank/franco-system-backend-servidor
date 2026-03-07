package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
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
import java.util.List;

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
     * Obtiene estadísticas de productos más vendidos con filtros usando
     * CriteriaBuilder
     */
    public List<ProductoVendidoEstadistica> obtenerProductosMasVendidos(LocalDateTime inicio, LocalDateTime fin,
            Integer limit, Long sucursalId, Long familiaId) {
        String sql = "SELECT p.id, p.descripcion, SUM(vi.cantidad) as cantidad, " +
                "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) as total_monto " +
                "FROM operaciones.venta_item vi " +
                "JOIN productos.producto p ON vi.producto_id = p.id " +
                "JOIN operaciones.venta v ON vi.venta_id = v.id AND vi.sucursal_id = v.sucursal_id " +
                "LEFT JOIN productos.subfamilia sf ON p.sub_familia_id = sf.id " +
                "WHERE v.estado = 'CONCLUIDA' AND vi.activo = true ";

        if (inicio != null) sql += "AND vi.creado_en >= :inicio ";
        if (fin != null) sql += "AND vi.creado_en < :fin ";
        if (sucursalId != null && sucursalId > 0) sql += "AND vi.sucursal_id = :sucursalId ";
        if (familiaId != null && familiaId > 0) sql += "AND sf.familia_id = :familiaId ";

        sql += "GROUP BY p.id, p.descripcion ORDER BY total_monto DESC";

        javax.persistence.Query query = em.createNativeQuery(sql);
        if (inicio != null) query.setParameter("inicio", inicio);
        if (fin != null) query.setParameter("fin", fin);
        if (sucursalId != null && sucursalId > 0) query.setParameter("sucursalId", sucursalId);
        if (familiaId != null && familiaId > 0) query.setParameter("familiaId", familiaId);

        query.setMaxResults(limit != null ? limit : 10);

        List<Object[]> resultados = query.getResultList();

        return transformarResultadosAEstadisticas(resultados);
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

            Double porcentaje = 0.0;
            if (montoTotalGeneral > 0) {
                porcentaje = (totalMonto * 100.0) / montoTotalGeneral;
                porcentaje = Math.round(porcentaje * 100.0) / 100.0;
            }

            estadisticas.add(new ProductoVendidoEstadistica(
                    productoId,
                    descripcion,
                    cantidad,
                    totalMonto,
                    porcentaje));
        }

        return estadisticas;
    }
}