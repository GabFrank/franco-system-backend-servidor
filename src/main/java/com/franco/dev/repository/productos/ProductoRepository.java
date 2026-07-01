package com.franco.dev.repository.productos;

import com.franco.dev.domain.dto.ProductoIdAndCantidadDto;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductoRepository extends HelperRepository<Producto, Long> {

        default Class<Producto> getEntityClass() {
                return Producto.class;
        }

        public Producto findByDescripcion(String texto);

        @Query("SELECT p FROM Producto p WHERE UPPER(TRIM(p.descripcion)) = UPPER(TRIM(?1)) " +
               "OR UPPER(TRIM(p.descripcionFactura)) = UPPER(TRIM(?1))")
        public List<Producto> findByDescripcionNormalized(String descripcion);

        @Query(value = "SELECT p.id FROM productos.producto p " +
                        "WHERE UPPER(p.descripcion) LIKE CONCAT('%', UPPER(?1), '%') " +
                        "OR UPPER(p.descripcion_factura) LIKE CONCAT('%', UPPER(?1), '%') " +
                        "ORDER BY p.descripcion ASC " +
                        "LIMIT ?2", nativeQuery = true)
        List<Long> findIdsByDescripcionLike(String texto, int limite);

        @Query(value = "select distinct on (p.id, p.descripcion) p.* " +
                        "from productos.producto p " +
                        "left outer join productos.presentacion p2 on p2.producto_id = p.id " +
                        "left outer join productos.codigo c on c.presentacion_id = p2.id " +
                        "left join ( " +
                        "   select m.producto_id, sum(m.cantidad) as stock_actual " +
                        "   from operaciones.movimiento_stock m " +
                        "   where m.sucursal_id = CAST(:sucursalId AS bigint) and m.estado = true " +
                        "   group by m.producto_id " +
                        ") st on st.producto_id = p.id " +
                        "where (CAST(p.id as text) like CONCAT('%', :texto, '%') or UPPER(p.descripcion) like CONCAT('%', UPPER(:texto), '%') "
                        +
                        "or UPPER(p.descripcion_factura) like CONCAT('%', UPPER(:texto), '%') or c.codigo like CONCAT('%', :texto, '%')) "
                        +
                        "and p.activo = true " +
                        "and EXISTS (SELECT 1 FROM productos.presentacion p3 WHERE p3.producto_id = p.id AND p3.activo = true) "
                        +
                        "and (:conStock = 'false' OR (COALESCE(st.stock_actual, 0) > 0)) " +
                        "ORDER BY p.descripcion asc " +
                        "limit 10 " +
                        "offset :offset", nativeQuery = true)
        public List<Producto> findbyAll(
                        @Param("texto") String texto,
                        @Param("offset") int offset,
                        @Param("sucursalId") String sucursalId,
                        @Param("conStock") String conStock);

        @Query(value = "select distinct on (p.id, p.descripcion) p.* " +
                        "from productos.producto p  " +
                        "left outer join productos.presentacion p2 on p2.producto_id = p.id  " +
                        "left outer join productos.codigo c on c.presentacion_id = p2.id  " +
                        "where (CAST(p.id as text) like CONCAT('%', ?1, '%') or UPPER(p.descripcion) like CONCAT('%', UPPER(?1), '%') or UPPER(p.descripcion_factura) like CONCAT('%', UPPER(?1), '%') or c.codigo like CONCAT('%', ?1, '%')) and p.activo = true "
                        +
                        "ORDER BY p.descripcion asc", nativeQuery = true)
        public List<Producto> findForReport(String texto);

        @Query(value = "select distinct on (p.id, p.descripcion) p.*   " +
                        "from productos.producto p  " +
                        "left outer join productos.presentacion p2 on p2.producto_id = p.id  " +
                        "left outer join productos.codigo c on c.presentacion_id = p2.id  " +
                        "where p.is_envase = true and (CAST(p.id as text) like CONCAT('%', ?1, '%') or UPPER(p.descripcion) like CONCAT('%', UPPER(?1), '%')) "
                        +
                        "ORDER BY p.descripcion asc  " +
                        "limit 10 " +
                        "offset ?2", nativeQuery = true)
        public List<Producto> findEnvases(String texto, int offset, Boolean isEnvase);

        public List<Producto> findBySubfamiliaId(Long id);

        @Query("select distinct pro FROM ProductoProveedor ci " +
                        "left outer join ci.proveedor as p " +
                        "left outer join ci.producto as pro " +
                        "where p.id = ?1 AND (CAST(pro.id as text) like %?2% or UPPER(pro.descripcion) like %?2% or UPPER(pro.descripcionFactura) like %?2%)")
        public List<Producto> findByProveedorId(Long id, String text);

        @Query(value = "select * from productos.producto p " +
                        "left join productos.presentacion p2 on p2.producto_id = p.id " +
                        "left join productos.codigo c on c.presentacion_id = p2.id " +
                        "where c.codigo = ?1", nativeQuery = true)
        public Producto findByCodigo(String texto);

        @Query(value = "select * from productos.producto p  " +
                        "where p.activo = true " +
                        "and EXISTS (SELECT 1 FROM productos.presentacion p3 WHERE p3.producto_id = p.id AND p3.activo = true)", nativeQuery = true)
        public List<Producto> findAllForPdv();

        @Query(value = "select * from productos.producto p " +
                        "join productos.pdv_grupos_productos pgp on pgp.producto_id = p.id " +
                        "where pgp.id = ?1 " +
                        "and EXISTS (SELECT 1 FROM productos.presentacion p3 WHERE p3.producto_id = p.id AND p3.activo = true)", nativeQuery = true)
        public List<Producto> findByPdvGrupoProductoId(Long id);

        @Query("SELECT new com.franco.dev.domain.dto.ProductoIdAndCantidadDto(pro.id, SUM(vi.cantidad * pre.cantidad)) "
                        +
                        "FROM VentaItem vi " +
                        "JOIN vi.venta v " +
                        "JOIN vi.presentacion pre " +
                        "JOIN vi.producto pro " +
                        "WHERE vi.sucursalId = :sucId AND v.creadoEn BETWEEN :inicio AND :fin " +
                        "GROUP BY pro.id")
        public List<ProductoIdAndCantidadDto> findProductosAndCantidadVendidaPorPeriodoAndSucursal(
                        @Param("sucId") Long sucId, @Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);

        @Query("SELECT Distinct new com.franco.dev.domain.operaciones.dto.LucroPorProductosDto(" +
                        "pro.id as id, " +
                        "pro.descripcion as descripcion, " +
                        "SUM(vi.cantidad * pre.cantidad * COALESCE(vi.precioCosto, cpp.ultimoPrecioCompra, 0)) as costoTotal, "
                        +
                        "SUM(vi.cantidad * pre.cantidad) as cantidad, " +
                        "SUM(vi.precio * vi.cantidad * pre.cantidad) as totalVenta," +
                        "0.0, 0.0, 0.0, 0.0, " +
                        "SUM(CASE WHEN (v.totalGs > 0) THEN ((vi.precio * vi.cantidad) / v.totalGs * COALESCE((SELECT SUM(cd.valor * cd.cambio) FROM CobroDetalle cd WHERE cd.cobro = v.cobro AND cd.descuento = true), 0.0)) ELSE 0.0 END), "
                        +
                        "SUM(CASE WHEN (v.totalGs > 0) THEN ((vi.precio * vi.cantidad) / v.totalGs * COALESCE((SELECT SUM(cd.valor * cd.cambio) FROM CobroDetalle cd WHERE cd.cobro = v.cobro AND cd.aumento = true), 0.0)) ELSE 0.0 END) "
                        +
                        ") " +
                        "FROM VentaItem vi " +
                        "JOIN vi.venta v " +
                        "JOIN v.usuario u " +
                        "JOIN vi.presentacion pre " +
                        "JOIN vi.producto pro " +
                        "LEFT JOIN CostoPorProducto cpp ON cpp.producto.id = pro.id AND cpp.id = (" +
                        "SELECT MAX(innerCpp.id) FROM CostoPorProducto innerCpp WHERE innerCpp.producto.id = pro.id" +
                        ") " +
                        "WHERE " +
                        "v.estado = 'CONCLUIDA' AND " +
                        "v.creadoEn BETWEEN :startDate AND :endDate AND " +
                        "(:sucursalId is null or v.sucursalId = :sucursalId) AND " +
                        "(:filtrarUsuario = false or u.id IN (:usuarioIdList)) AND " +
                        "(:filtrarProducto = false or pro.id IN (:productoIdList)) AND " +
                        "((:subfamiliaId) is null or pro.subfamilia.id = :subfamiliaId) AND " +
                        "((:familiaId) is null or pro.subfamilia.id IN (SELECT sf.id FROM Subfamilia sf WHERE sf.familia.id = :familiaId)) " +
                        "group by pro.id " +
                        "ORDER BY SUM(vi.precio * vi.cantidad * pre.cantidad) DESC")
        public List<LucroPorProductosDto> findLucroPorProducto(
                        @Param("sucursalId") Long sucursalId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("usuarioIdList") List<Long> usuarioIdList,
                        @Param("productoIdList") List<Long> productoIdList,
                        @Param("subfamiliaId") Long subfamiliaId,
                        @Param("familiaId") Long familiaId,
                        @Param("filtrarUsuario") Boolean filtrarUsuario,
                        @Param("filtrarProducto") Boolean filtrarProducto);

        @Query("SELECT pro.id, SUM(vi.precio * vi.cantidad) " +
                        "FROM VentaItem vi " +
                        "JOIN vi.venta v " +
                        "JOIN v.usuario u " +
                        "JOIN vi.presentacion pre " +
                        "JOIN vi.producto pro " +
                        "WHERE " +
                        "v.estado = 'CONCLUIDA' AND " +
                        "v.creadoEn BETWEEN :startDate AND :endDate AND " +
                        "((:sucursalId) is null or v.sucursalId = (:sucursalId)) AND " +
                        "(:filtrarUsuario = false or u.id IN (:usuarioIdList)) AND " +
                        "(:filtrarProducto = false or pro.id IN (:productoIdList)) AND " +
                        "((:subfamiliaId) is null or pro.subfamilia.id = :subfamiliaId) AND " +
                        "((:familiaId) is null or pro.subfamilia.id IN (SELECT sf.id FROM Subfamilia sf WHERE sf.familia.id = :familiaId)) " +
                        "GROUP BY pro.id")
        public List<Object[]> findTotalVentaPacksPorProducto(
                        @Param("sucursalId") Long sucursalId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("usuarioIdList") List<Long> usuarioIdList,
                        @Param("productoIdList") List<Long> productoIdList,
                        @Param("subfamiliaId") Long subfamiliaId,
                        @Param("familiaId") Long familiaId,
                        @Param("filtrarUsuario") Boolean filtrarUsuario,
                        @Param("filtrarProducto") Boolean filtrarProducto);

        /** Misma base que {@link #findTotalVentaPacksPorProducto}, agrupado por sucursal (gráfico ventas). */
        @Query("SELECT v.sucursalId, s.nombre, SUM(vi.precio * vi.cantidad), COUNT(DISTINCT v.id) " +
                        "FROM VentaItem vi " +
                        "JOIN vi.venta v " +
                        "JOIN v.usuario u " +
                        "JOIN vi.presentacion pre " +
                        "JOIN vi.producto pro " +
                        "JOIN v.sucursal s " +
                        "WHERE v.estado = 'CONCLUIDA' AND " +
                        "v.creadoEn BETWEEN :startDate AND :endDate " +
                        "GROUP BY v.sucursalId, s.nombre " +
                        "ORDER BY SUM(vi.precio * vi.cantidad) DESC")
        List<Object[]> findTotalVentaPorSucursal(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /** Misma base que {@link #findTotalVentaPorSucursal}, agrupado por ciudad de la sucursal. */
        @Query("SELECT COALESCE(c.id, -1L), COALESCE(c.descripcion, 'Sin Ciudad'), SUM(vi.precio * vi.cantidad), COUNT(DISTINCT v.id) " +
                        "FROM VentaItem vi " +
                        "JOIN vi.venta v " +
                        "JOIN v.usuario u " +
                        "JOIN vi.presentacion pre " +
                        "JOIN vi.producto pro " +
                        "JOIN v.sucursal s " +
                        "LEFT JOIN s.ciudad c " +
                        "WHERE v.estado = 'CONCLUIDA' AND " +
                        "v.creadoEn BETWEEN :startDate AND :endDate " +
                        "GROUP BY COALESCE(c.id, -1L), COALESCE(c.descripcion, 'Sin Ciudad') " +
                        "ORDER BY SUM(vi.precio * vi.cantidad) DESC")
        List<Object[]> findTotalVentaPorCiudad(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query("select distinct p from Producto p " +
                        "left join Presentacion pres on pres.producto.id = p.id " +
                        "left join Codigo cod on cod.presentacion.id = pres.id " +
                        "where  (CAST(p.id as string) like CONCAT('%', :texto, '%') or UPPER(p.descripcion) like CONCAT('%', UPPER(:texto), '%') "
                        +
                        "or UPPER(p.descripcionFactura) like CONCAT('%', UPPER(:texto), '%')) " +
                        "and ((:activo) is null or p.activo = :activo) " +
                        "and ((:stock) is null or p.stock = :stock) " +
                        "and ((:balanza) is null or p.balanza = :balanza) " +
                        "and ((:vencimiento) is null or p.vencimiento = :vencimiento) " +
                        "and ((:subfamiliaId) is null or p.subfamilia.id = :subfamiliaId) " +
                        "and ((:familiaId) is null or p.subfamilia.id IN " +
                        "     (SELECT sf.id FROM Subfamilia sf WHERE sf.familia.id = :familiaId)) " +
                        "and ((:costoCero) is null or " +
                        "     ((:costoCero) = true and (p.id IN (SELECT c1.producto.id FROM CostoPorProducto c1 " +
                        "WHERE (c1.costoMedio = 0 OR c1.costoMedio IS NULL) AND c1.creadoEn = (SELECT MAX(c2.creadoEn) "
                        +
                        "FROM CostoPorProducto c2 WHERE c2.producto.id = c1.producto.id)) " +
                        "OR p.id NOT IN (SELECT DISTINCT c4.producto.id FROM CostoPorProducto c4))) or " +
                        "     ((:costoCero) = false and p.id NOT IN (SELECT c1.producto.id FROM CostoPorProducto c1 " +
                        "WHERE (c1.costoMedio = 0 OR c1.costoMedio IS NULL) AND c1.creadoEn = (SELECT MAX(c2.creadoEn) FROM CostoPorProducto c2 "
                        +
                        "WHERE c2.producto.id = c1.producto.id)) AND EXISTS (SELECT 1 FROM CostoPorProducto c3 " +
                        "WHERE c3.producto.id = p.id AND c3.costoMedio > 0))) " +
                        "and ((:stockFiltro) is null or :stockFiltro = 'todos' or " +
                        "     ((:stockFiltro) = 'positivo' and p.id IN (SELECT ms.producto.id FROM MovimientoStock ms "
                        +
                        "WHERE ms.estado = true AND ((:sucursalId) is null or ms.sucursalId = :sucursalId) " +
                        "GROUP BY ms.producto.id HAVING SUM(ms.cantidad) > 0)) or " +
                        "     ((:stockFiltro) = 'negativo' and p.id IN (SELECT ms.producto.id FROM MovimientoStock ms "
                        +
                        "WHERE ms.estado = true AND ((:sucursalId) is null or ms.sucursalId = :sucursalId) " +
                        "GROUP BY ms.producto.id HAVING SUM(ms.cantidad) < 0))) " +
                        "order by p.id asc")
        public Page<Producto> searchWithFilters(
                        @Param("texto") String texto,
                        @Param("activo") Boolean activo,
                        @Param("stock") Boolean stock,
                        @Param("balanza") Boolean balanza,
                        @Param("subfamiliaId") Long subfamiliaId,
                        @Param("familiaId") Long familiaId,
                        @Param("vencimiento") Boolean vencimiento,
                        @Param("costoCero") Boolean costoCero,
                        @Param("stockFiltro") String stockFiltro,
                        @Param("sucursalId") Long sucursalId,
                        Pageable pageable);

        @Query("select distinct p from Producto p " +
                        "where p.id in :ids " +
                        "and ((:activo) is null or p.activo = :activo) " +
                        "and ((:stock) is null or p.stock = :stock) " +
                        "and ((:balanza) is null or p.balanza = :balanza) " +
                        "and ((:vencimiento) is null or p.vencimiento = :vencimiento) " +
                        "and ((:subfamiliaId) is null or p.subfamilia.id = :subfamiliaId) " +
                        "and ((:familiaId) is null or p.subfamilia.id IN " +
                        "     (SELECT sf.id FROM Subfamilia sf WHERE sf.familia.id = :familiaId)) " +
                        "and ((:costoCero) is null or " +
                        "     ((:costoCero) = true and (p.id IN (SELECT c1.producto.id FROM CostoPorProducto c1 " +
                        "WHERE (c1.costoMedio = 0 OR c1.costoMedio IS NULL) AND c1.creadoEn = (SELECT MAX(c2.creadoEn) "
                        +
                        "FROM CostoPorProducto c2 WHERE c2.producto.id = c1.producto.id)) " +
                        "OR p.id NOT IN (SELECT DISTINCT c4.producto.id FROM CostoPorProducto c4))) or " +
                        "     ((:costoCero) = false and p.id NOT IN (SELECT c1.producto.id FROM CostoPorProducto c1 " +
                        "WHERE (c1.costoMedio = 0 OR c1.costoMedio IS NULL) AND c1.creadoEn = (SELECT MAX(c2.creadoEn) FROM CostoPorProducto c2 "
                        +
                        "WHERE c2.producto.id = c1.producto.id)) AND EXISTS (SELECT 1 FROM CostoPorProducto c3 " +
                        "WHERE c3.producto.id = p.id AND c3.costoMedio > 0))) " +
                        "and ((:stockFiltro) is null or :stockFiltro = 'todos' or " +
                        "     ((:stockFiltro) = 'positivo' and p.id IN (SELECT ms.producto.id FROM MovimientoStock ms "
                        +
                        "WHERE ms.estado = true AND ((:sucursalId) is null or ms.sucursalId = :sucursalId) " +
                        "GROUP BY ms.producto.id HAVING SUM(ms.cantidad) > 0)) or " +
                        "     ((:stockFiltro) = 'negativo' and p.id IN (SELECT ms.producto.id FROM MovimientoStock ms "
                        +
                        "WHERE ms.estado = true AND ((:sucursalId) is null or ms.sucursalId = :sucursalId) " +
                        "GROUP BY ms.producto.id HAVING SUM(ms.cantidad) < 0)))")
        List<Producto> searchWithFiltersByIds(
                        @Param("ids") List<Long> ids,
                        @Param("activo") Boolean activo,
                        @Param("stock") Boolean stock,
                        @Param("balanza") Boolean balanza,
                        @Param("subfamiliaId") Long subfamiliaId,
                        @Param("familiaId") Long familiaId,
                        @Param("vencimiento") Boolean vencimiento,
                        @Param("costoCero") Boolean costoCero,
                        @Param("stockFiltro") String stockFiltro,
                        @Param("sucursalId") Long sucursalId);
}