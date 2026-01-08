package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaItemRepository extends HelperRepository<VentaItem, EmbebedPrimaryKey> {

        default Class<VentaItem> getEntityClass() {
                return VentaItem.class;
        }

        // public List<Venta> findByProveedorPersonaNombreContainingIgnoreCase(String
        // texto);

        // @Query("select p from Venta p left outer join p.proveedor as pro left outer
        // join pro.persona as per where LOWER(per.nombre) like %?1%")
        // public List<Venta> findByProveedor(String texto);

        // @Query("select p from Producto p where CAST(id as text) like %?1% or
        // UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
        // public List<Producto> findbyAll(String texto);

        public List<VentaItem> findByVentaIdAndSucursalId(Long id, Long sucId);

        VentaItem findByIdAndSucursalId(Long id, Long sucId);

        Boolean deleteByIdAndSucursalId(Long id, Long sucId);

        @Query(value = "select sum(vi.cantidad * vi.precio) from operaciones.venta_item vi " +
                        "where vi.venta_id = ?1 and vi.sucursal_id = ?2", nativeQuery = true)
        Double totalByVentaIdAndSucId(Long id, Long sucId);

        @Query(value = "SELECT p.id, p.descripcion, SUM(vi.cantidad) as total_cantidad, " +
                        "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) as total_monto "
                        +
                        "FROM operaciones.venta_item vi " +
                        "JOIN productos.producto p ON vi.producto_id = p.id " +
                        "JOIN operaciones.venta v ON vi.venta_id = v.id AND vi.sucursal_id = v.sucursal_id " +
                        "WHERE v.estado = 'CONCLUIDA' " +
                        "AND vi.creado_en >= ?1 AND vi.creado_en < ?2 " +
                        "AND vi.activo = true " +
                        "GROUP BY p.id, p.descripcion " +
                        "ORDER BY total_monto DESC " +
                        "LIMIT ?3", nativeQuery = true)
        List<Object[]> obtenerProductosMasVendidos(LocalDateTime inicio, LocalDateTime fin, Integer limit);

        @Query(value = "SELECT p.id, p.descripcion, SUM(vi.cantidad) as total_cantidad, " +
                        "SUM((vi.precio * vi.cantidad) - COALESCE(vi.descuento_unitario * vi.cantidad, 0)) as total_monto "
                        +
                        "FROM operaciones.venta_item vi " +
                        "JOIN productos.producto p ON vi.producto_id = p.id " +
                        "JOIN operaciones.venta v ON vi.venta_id = v.id AND vi.sucursal_id = v.sucursal_id " +
                        "WHERE v.estado = 'CONCLUIDA' " +
                        "AND vi.creado_en >= ?1 AND vi.creado_en < ?2 " +
                        "AND vi.sucursal_id = ?4 " +
                        "AND vi.activo = true " +
                        "GROUP BY p.id, p.descripcion " +
                        "ORDER BY total_monto DESC " +
                        "LIMIT ?3", nativeQuery = true)
        List<Object[]> obtenerProductosMasVendidosPorSucursal(LocalDateTime inicio, LocalDateTime fin, Integer limit,
                        Long sucursalId);
}