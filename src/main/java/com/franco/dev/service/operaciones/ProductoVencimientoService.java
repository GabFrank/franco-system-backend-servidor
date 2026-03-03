package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.ProductoVencimiento;
import com.franco.dev.repository.operaciones.ProductoVencimientoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class ProductoVencimientoService extends CrudService<ProductoVencimiento, ProductoVencimientoRepository, Long> {

    private final ProductoVencimientoRepository repository;

    @Override
    public ProductoVencimientoRepository getRepository() {
        return repository;
    }

    @Override
    @Transactional
    public ProductoVencimiento save(ProductoVencimiento entity) {
        if (entity.getFechaCreacion() == null) {
            entity.setFechaCreacion(LocalDateTime.now());
        }
        return super.save(entity);
    }
}

