package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductoProveedorRepository extends HelperRepository<ProductoProveedor, Long> {

    default Class<ProductoProveedor> getEntityClass() {
        return ProductoProveedor.class;
    }

    Page<ProductoProveedor> findByProveedorId(Long id, Pageable pageable);
    Page<ProductoProveedor> findByProductoId(Long id, Pageable pageable);
}
