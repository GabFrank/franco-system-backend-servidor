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
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<VentaItem> item = query.from(VentaItem.class);
        Join<VentaItem, Producto> producto = item.join("producto");
        Join<VentaItem, Venta> venta = item.join("venta");

        // Predicados base
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(venta.get("estado"), VentaEstado.CONCLUIDA));
        predicates.add(cb.equal(item.get("activo"), true));

        if (inicio != null) {
            predicates.add(cb.greaterThanOrEqualTo(item.get("creadoEn"), inicio));
        }
        if (fin != null) {
            predicates.add(cb.lessThan(item.get("creadoEn"), fin));
        }

        // Filtros opcionales
        if (sucursalId != null && sucursalId > 0) {
            predicates.add(cb.equal(item.get("sucursalId"), sucursalId));
        }
        if (familiaId != null && familiaId > 0) {
            predicates.add(cb.equal(producto.get("subfamilia").get("familia").get("id"), familiaId));
        }

        query.where(predicates.toArray(new Predicate[0]));

        // Selección y Agrupación
        // total_monto = SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario
        // * vi.cantidad, 0))
        Expression<Double> multiplicacionPrecioCantidad = cb.prod(item.get("precio"), item.get("cantidad"));
        Expression<Double> descuentoTotal = cb.coalesce(cb.prod(item.get("valorDescuento"), item.get("cantidad")), 0.0);
        Expression<Double> montoFila = cb.diff(multiplicacionPrecioCantidad, descuentoTotal);

        query.multiselect(
                producto.get("id"),
                producto.get("descripcion"),
                cb.sum(item.get("cantidad")),
                cb.sum(montoFila));

        query.groupBy(producto.get("id"), producto.get("descripcion"));
        query.orderBy(cb.desc(cb.sum(montoFila)));

        List<Object[]> resultados = em.createQuery(query)
                .setMaxResults(limit != null ? limit : 10)
                .getResultList();

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