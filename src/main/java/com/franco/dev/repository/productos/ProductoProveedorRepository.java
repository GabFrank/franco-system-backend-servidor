package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface ProductoProveedorRepository extends HelperRepository<ProductoProveedor, Long> {

    default Class<ProductoProveedor> getEntityClass() {
        return ProductoProveedor.class;
    }

    Page<ProductoProveedor> findByProveedorIdOrderByProductoDescripcionAsc(Long id, Pageable pageable);

    @Query(value =  "select pp from ProductoProveedor pp " +
            "join pp.proveedor prov " +
            "join pp.producto prod where " +
            "prov.id = :id and " +
            "(:text is null or UPPER(prod.descripcion) like UPPER(:text)) " +
            "order by prod.descripcion asc")
    Page<ProductoProveedor> findByProveedorIdAndProductoDescripcionLikeIgnoreCase(Long id, String text, Pageable pageable);

    Page<ProductoProveedor> findByProductoId(Long id, Pageable pageable);
}
