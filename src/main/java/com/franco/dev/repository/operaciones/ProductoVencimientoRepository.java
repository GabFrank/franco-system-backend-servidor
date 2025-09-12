package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.ProductoVencimiento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoVencimientoRepository extends HelperRepository<ProductoVencimiento, Long> {
    
    default Class<ProductoVencimiento> getEntityClass() {
        return ProductoVencimiento.class;
    }
}

