package com.franco.dev.repository.productos;

import com.franco.dev.domain.dto.ProductoIdAndCantidadDto;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductoRepository extends HelperRepository<Producto, Long> {

    default Class<Producto> getEntityClass() {
        return Producto.class;
    }

    public Producto findByDescripcion(String texto);

    @Query(value = "select distinct on (p.id, p.descripcion) p.id, p.descripcion, p.balanza , p.cambiable , p.combo , p.creado_en , p.descripcion_factura , p.dias_vencimiento, p.es_alcoholico , p.garantia, p.imagenes , p.ingrediente , p.iva, p.observacion , p.promocion , p.propagado , p.stock , p.sub_familia_id , p.tiempo_garantia , p.tipo_conservacion , p.unidad_por_caja , p.unidad_por_caja_secundaria , p.usuario_id , p.vencimiento, p.is_envase, p.envase_id   " +
            "from productos.producto p  " +
            "left outer join productos.presentacion p2 on p2.producto_id = p.id  " +
            "left outer join productos.codigo c on c.presentacion_id = p2.id  " +
            "where CAST(p.id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcion_factura) like %?1% or c.codigo like %?1% " +
            "ORDER BY p.descripcion asc  " +
            "limit 10 " +
            "offset ?2", nativeQuery = true)
    public List<Producto> findbyAll(String texto, int offset);

    @Query(value = "select distinct on (p.id, p.descripcion) p.id, p.descripcion, p.balanza , p.cambiable , p.combo , p.creado_en , p.descripcion_factura , p.dias_vencimiento, p.es_alcoholico , p.garantia, p.imagenes , p.ingrediente , p.iva, p.observacion , p.promocion , p.propagado , p.stock , p.sub_familia_id , p.tiempo_garantia , p.tipo_conservacion , p.unidad_por_caja , p.unidad_por_caja_secundaria , p.usuario_id , p.vencimiento,p.is_envase, p.envase_id   " +
            "from productos.producto p  " +
            "left outer join productos.presentacion p2 on p2.producto_id = p.id  " +
            "left outer join productos.codigo c on c.presentacion_id = p2.id  " +
            "where p.is_envase = true and CAST(p.id as text) like %?1% or UPPER(p.descripcion) like %?1% " +
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
            "where p.activo = true", nativeQuery = true)
    public List<Producto> findAllForPdv();

    @Query(value = "select * from productos.producto p " +
            "join productos.pdv_grupos_productos pgp on pgp.producto_id = p.id " +
            "where pgp.id = ?1", nativeQuery = true)
    public List<Producto> findByPdvGrupoProductoId(Long id);

    @Query("SELECT new com.franco.dev.domain.dto.ProductoIdAndCantidadDto(pro.id, SUM(vi.cantidad * pre.cantidad)) " +
            "FROM VentaItem vi " +
            "JOIN vi.venta v " +
            "JOIN vi.presentacion pre " +
            "JOIN vi.producto pro " +
            "WHERE vi.sucursalId = :sucId AND v.creadoEn BETWEEN :inicio AND :fin " +
            "GROUP BY pro.id")
    public List<ProductoIdAndCantidadDto> findProductosAndCantidadVendidaPorPeriodoAndSucursal(@Param("sucId") Long sucId, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT Distinct new com.franco.dev.domain.operaciones.dto.LucroPorProductosDto(" +
            "pro.id as id, " +
            "pro.descripcion as descripcion, " +
            "SUM(vi.cantidad * pre.cantidad * COALESCE(vi.precioCosto, cpp.ultimoPrecioCompra, 0)) as costoUnitario, " +
            "SUM(vi.cantidad * pre.cantidad) as cantidad, " +
            "SUM(vi.precio * vi.cantidad) as totalVenta," +
            "0.0, 0.0, 0.0, 0.0 " +
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
            "((:sucursalIdList) is null or v.sucursalId IN (:sucursalIdList)) AND " +
            "((:usuarioIdList) is null or u.id IN (:usuarioIdList)) AND " +
            "((:productoIdList) is null or pro.id IN (:productoIdList)) " +
            "group by pro.id " +
            "ORDER BY SUM(vi.precio * vi.cantidad) DESC")
    public List<LucroPorProductosDto> findLucroPorProducto(
            @Param("sucursalIdList") List<Long> sucursalIdList,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("usuarioIdList") List<Long> usuarioIdList,
            @Param("productoIdList") List<Long> productoIdList);
}
