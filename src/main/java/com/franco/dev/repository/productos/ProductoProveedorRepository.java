package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoProveedorRepository extends HelperRepository<ProductoProveedor, Long> {

    default Class<ProductoProveedor> getEntityClass() {
        return ProductoProveedor.class;
    }

    @Query("SELECT pp FROM ProductoProveedor pp " +
            "WHERE pp.id IN (" +
            "    SELECT MIN(subpp.id) " +
            "    FROM ProductoProveedor subpp " +
            "    WHERE subpp.proveedor.id = :id " +
            "    AND (subpp.activo = true OR subpp.activo IS NULL) " +
            "    GROUP BY subpp.producto.id" +
            ") " +
            "ORDER BY pp.producto.descripcion ASC")
    Page<ProductoProveedor> findByProveedorIdOrderByProductoDescripcionAsc(@Param("id") Long id, Pageable pageable);

    @Query(value =  "select pp from ProductoProveedor pp " +
            "join pp.proveedor prov " +
            "join pp.producto prod where " +
            "prov.id = :id and " +
            "(pp.activo = true OR pp.activo IS NULL) and " +
            "(:text is null or UPPER(prod.descripcion) like UPPER(:text)) " +
            "order by prod.descripcion asc")
    Page<ProductoProveedor> findByProveedorIdAndProductoDescripcionLikeIgnoreCase(Long id, String text, Pageable pageable);

    @Query("SELECT pp.producto.id FROM ProductoProveedor pp " +
            "WHERE pp.proveedor.id = :proveedorId " +
            "AND pp.producto.id IN :productoIds " +
            "AND (pp.activo = true OR pp.activo IS NULL)")
    List<Long> findProductoIdsByProveedorIdAndProductoIdIn(
            @Param("proveedorId") Long proveedorId,
            @Param("productoIds") List<Long> productoIds);

    @Query("SELECT pp FROM ProductoProveedor pp " +
            "LEFT JOIN pp.proveedor prov " +
            "LEFT JOIN prov.persona per " +
            "WHERE pp.producto.id = :id AND (pp.activo = true OR pp.activo IS NULL) " +
            "ORDER BY per.nombre ASC")
    Page<ProductoProveedor> findByProductoIdAndActivoTrueOrderByProveedorPersonaNombre(@Param("id") Long id, Pageable pageable);

    @Query("SELECT pp FROM ProductoProveedor pp " +
            "WHERE pp.producto.id = :productoId AND pp.proveedor.id = :proveedorId")
    ProductoProveedor findByProductoIdAndProveedorId(@Param("productoId") Long productoId, @Param("proveedorId") Long proveedorId);
}
