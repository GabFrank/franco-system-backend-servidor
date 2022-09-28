package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.repository.productos.ProductoProveedorRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProductoProveedorService extends CrudService<ProductoProveedor, ProductoProveedorRepository, Long> {

    @Autowired
    private final ProductoProveedorRepository repository;
//    private final PersonaPublisher personaPublisher;


    @Override
    public ProductoProveedorRepository getRepository() {
        return repository;
    }


}
