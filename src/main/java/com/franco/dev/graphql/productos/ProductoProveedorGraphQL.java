package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.ProductoProveedor;
import com.franco.dev.graphql.productos.input.ProductoProveedorInput;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoProveedorService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private ProveedorService proveedorService;

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

    public Page<ProductoProveedor> productoProveedorPorProductoId(Long id, Integer page, Integer size) {
        if (page == null) page = 0;
        if (size == null) size = 10;
        Pageable pageable = PageRequest.of(page, size);
        return service.findByProductoId(id, pageable);
    }

    @Transactional
    public ProductoProveedor saveProductoProveedor(ProductoProveedorInput input) {
        if (input.getProductoId() == null) {
            throw new GraphQLException("El producto es requerido");
        }
        if (input.getProveedorId() == null) {
            throw new GraphQLException("El proveedor es requerido");
        }
        ProductoProveedor existente = service.getRepository().findByProductoIdAndProveedorId(input.getProductoId(), input.getProveedorId());
        if (existente != null && Boolean.TRUE.equals(existente.getActivo())) {
            throw new GraphQLException("Este producto ya está vinculado a este proveedor");
        }
        if (existente != null && Boolean.FALSE.equals(existente.getActivo())) {
            existente.setActivo(true);
            existente.setMotivoDesvinculacion(null);
            existente.setCreadoEn(LocalDateTime.now());
            if (input.getUsuarioId() != null) {
                existente.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            return service.save(existente);
        }
        ProductoProveedor pp = new ProductoProveedor();
        pp.setProducto(productoService.findById(input.getProductoId())
                .orElseThrow(() -> new GraphQLException("Producto no encontrado con id: " + input.getProductoId())));
        pp.setProveedor(proveedorService.findById(input.getProveedorId())
                .orElseThrow(() -> new GraphQLException("Proveedor no encontrado con id: " + input.getProveedorId())));
        pp.setPedido(null);
        pp.setCreadoEn(LocalDateTime.now());
        if (input.getUsuarioId() != null) {
            pp.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        pp.setActivo(true);
        return service.save(pp);
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
