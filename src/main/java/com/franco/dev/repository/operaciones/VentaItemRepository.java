package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

        @Query("SELECT  new com.franco.dev.domain.operaciones.VentaPorFuncionario(p.id, p.descripcion, SUM(vi.precio), SUM(CAST(vi.cantidad AS long))) "
                        +
                        "FROM VentaItem vi " +
                        "JOIN vi.venta v " +
                        "JOIN vi.producto p " +
                        "WHERE v.usuario.id = :usuarioId " +
                        "AND v.creadoEn BETWEEN :inicio AND :fin " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY p.id, p.descripcion " +
                        "ORDER BY SUM(vi.cantidad) DESC")
        List<java.lang.Object> findTopProductoByUsuario(
                        @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
                        @org.springframework.data.repository.query.Param("inicio") java.time.LocalDateTime inicio,
                        @org.springframework.data.repository.query.Param("fin") java.time.LocalDateTime fin,
                        org.springframework.data.domain.Pageable pageable);

        // Primero las ventas del rango; descuentos/aumentos y el costo de respaldo se buscan solo para
        // ellas. Agrupar todo cobro_detalle y todo costo_por_producto tardaba ~23 s con 3 días.
        @Query(value = "WITH ventas AS ( " +
                        "  SELECT v.id, v.sucursal_id, v.usuario_id, v.cobro_id, v.total_gs " +
                        "  FROM operaciones.venta v " +
                        "  WHERE v.estado = 'CONCLUIDA' " +
                        "  AND v.creado_en BETWEEN :startDate AND :endDate " +
                        "  AND v.sucursal_id IN (:sucursalIdList) " +
                        "  AND (:filtrarUsuario = false OR v.usuario_id IN (:usuarioIdList)) " +
                        "), cd_agg AS ( " +
                        "  SELECT cd.cobro_id, cd.sucursal_id, " +
                        "    SUM(CASE WHEN cd.descuento = true THEN cd.valor * cd.cambio ELSE 0 END) AS desc_total, " +
                        "    SUM(CASE WHEN cd.aumento = true THEN cd.valor * cd.cambio ELSE 0 END) AS aum_total " +
                        "  FROM operaciones.cobro_detalle cd " +
                        "  WHERE (cd.descuento = true OR cd.aumento = true) " +
                        "  AND (cd.cobro_id, cd.sucursal_id) IN (SELECT cobro_id, sucursal_id FROM ventas) " +
                        "  GROUP BY cd.cobro_id, cd.sucursal_id " +
                        ") " +
                        "SELECT " +
                        "u.id AS usuario_id, " +
                        "COALESCE(per.nombre, u.nickname, '') AS nombre_funcionario, " +
                        // Venta sin costo: el respaldo es el costo medio, no el precio de una sola compra.
                        "SUM(vi.cantidad * pre.cantidad * COALESCE(vi.costo_unitario, NULLIF(cpp.costo_medio, 0), cpp.ultimo_precio_compra, 0)) AS costo_total, " +
                        "SUM(vi.cantidad * pre.cantidad) AS cantidad, " +
                        "SUM(vi.precio * vi.cantidad) AS total_venta, " +
                        "SUM(CASE WHEN v.total_gs > 0 " +
                        "THEN (vi.precio * vi.cantidad) / v.total_gs * COALESCE(cd_agg.desc_total, 0) ELSE 0 END) AS total_descuento, " +
                        "SUM(CASE WHEN v.total_gs > 0 " +
                        "THEN (vi.precio * vi.cantidad) / v.total_gs * COALESCE(cd_agg.aum_total, 0) ELSE 0 END) AS total_aumento " +
                        "FROM ventas v " +
                        "INNER JOIN operaciones.venta_item vi ON vi.venta_id = v.id AND vi.sucursal_id = v.sucursal_id " +
                        "INNER JOIN personas.usuario u ON u.id = v.usuario_id " +
                        "LEFT JOIN personas.persona per ON per.id = u.persona_id " +
                        "INNER JOIN productos.presentacion pre ON pre.id = vi.presentacion_id " +
                        "INNER JOIN productos.producto pro ON pro.id = vi.producto_id " +
                        "LEFT JOIN LATERAL ( " +
                        "  SELECT c.ultimo_precio_compra, c.costo_medio FROM productos.costo_por_producto c " +
                        "  WHERE c.producto_id = vi.producto_id ORDER BY c.id DESC LIMIT 1 " +
                        ") cpp ON vi.costo_unitario IS NULL " +
                        "LEFT JOIN cd_agg ON cd_agg.cobro_id = v.cobro_id AND cd_agg.sucursal_id = v.sucursal_id " +
                        "WHERE (:filtrarProducto = false OR pro.id IN (:productoIdList)) " +
                        "AND (:subfamiliaId IS NULL OR pro.sub_familia_id = :subfamiliaId) " +
                        "AND (:familiaId IS NULL OR pro.sub_familia_id IN ( " +
                        "  SELECT sf.id FROM productos.subfamilia sf WHERE sf.familia_id = :familiaId " +
                        ")) " +
                        "GROUP BY u.id, per.nombre, u.nickname " +
                        "ORDER BY total_venta DESC",
                        nativeQuery = true)
        List<Object[]> findLucroPorFuncionarioNative(
                        @Param("sucursalIdList") List<Long> sucursalIdList,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("usuarioIdList") List<Long> usuarioIdList,
                        @Param("productoIdList") List<Long> productoIdList,
                        @Param("subfamiliaId") Long subfamiliaId,
                        @Param("familiaId") Long familiaId,
                        @Param("filtrarUsuario") Boolean filtrarUsuario,
                        @Param("filtrarProducto") Boolean filtrarProducto);
}