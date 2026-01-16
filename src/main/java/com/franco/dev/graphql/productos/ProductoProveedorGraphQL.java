package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoProveedorService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class ProductoProveedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProductoProveedorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<ProductoProveedor> productoProveedor(Long id) {
        return service.findById(id);
    }

    public List<ProductoProveedor> productoProveedores(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Page<ProductoProveedor> productoProveedorPorProveedorId(Long id, String texto, Integer page, Integer size, Long pedidoId){
        if (page == null) page = 0;
        if (size == null) size = 10;
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductoProveedor> result = service.findByProveedorId(id, texto, pedidoId, pageable);
        return result;
    }

    @Transactional
    public ProductoProveedor desvincularProductoProveedor(Long id, String motivo) {
        Optional<ProductoProveedor> optional = service.findById(id);
        if (!optional.isPresent()) {
            throw new GraphQLException("ProductoProveedor no encontrado con id: " + id);
        }

        ProductoProveedor productoProveedor = optional.get();
        
        // Validar que el motivo no esté vacío
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new GraphQLException("El motivo de desvinculación es requerido");
        }
        
        // Desvincular producto
        productoProveedor.setActivo(false);
        productoProveedor.setMotivoDesvinculacion(motivo.toUpperCase());

        return service.save(productoProveedor);
    }

}
