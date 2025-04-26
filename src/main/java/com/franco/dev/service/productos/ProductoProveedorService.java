package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.repository.productos.ProductoProveedorRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProductoProveedorService extends CrudService<ProductoProveedor, ProductoProveedorRepository, Long> {

    @Autowired
    private final ProductoProveedorRepository repository;
//    private final PersonaPublisher personaPublisher;

    public Page<ProductoProveedor> findByProveedorId(Long id, String texto, Pageable pageable) {
        if(texto!=null){
            texto = "%"+texto.toUpperCase()+"%";
            return repository.findByProveedorIdAndProductoDescripcionLikeIgnoreCase(id,texto, pageable);
        }
        return repository.findByProveedorIdOrderByProductoDescripcionAsc(id, pageable);
    }

    public Page<ProductoProveedor> findByProductoId(Long id, Pageable pageable) {
        return repository.findByProveedorIdOrderByProductoDescripcionAsc(id, pageable);
    }

    @Override
    public ProductoProveedorRepository getRepository() {
        return repository;
    }

}
