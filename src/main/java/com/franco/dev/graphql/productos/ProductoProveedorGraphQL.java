package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoProveedorService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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

    public Optional<ProductoProveedor> productoProveedor(Long id) {
        return service.findById(id);
    }

    public List<ProductoProveedor> productoProveedores(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Page<ProductoProveedor> productoProveedorPorProveedorId(Long id, String texto, int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        Page<ProductoProveedor> result = service.findByProveedorId(id, texto, pageable);
        return result;
    }

}
