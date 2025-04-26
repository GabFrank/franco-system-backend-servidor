package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.InventarioProductoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.ZonaService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.InventarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.GraphQLException;
import graphql.GraphqlErrorException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class InventarioProductoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InventarioProductoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ZonaService zonaService;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<InventarioProducto> inventarioProducto(Long id) {
        return service.findById(id);
    }

    public List<InventarioProducto> inventarioProductos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public InventarioProducto saveInventarioProducto(InventarioProductoInput input) throws GraphqlErrorException {
        ModelMapper m = new ModelMapper();
        InventarioProducto e = m.map(input, InventarioProducto.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getProductoId() != null) {
            e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        } else {
            e.setProducto(null);
        }
        if (input.getZonaId() != null) e.setZona(zonaService.findById(input.getZonaId()).orElse(null));
        if (input.getInventarioId() != null)
            e.setInventario(inventarioService.findById(input.getInventarioId()).orElse(null));
        if(input.getId()==null && service.verificarUsuarioZonaAbierta(input.getInventarioId(), input.getUsuarioId())){
            throw new GraphQLException("Ya tenes una zona abierta.");
        }
        e = service.save(e);
        return e;
    }

    public Boolean verificarUsuarioZona(Long invId, Long usuId){
        return service.verificarUsuarioZonaAbierta(invId, usuId);
    }

    public Boolean deleteInventarioProducto(Long id) {
        Boolean ok = false;
        InventarioProducto i = service.findById(id).orElse(null);
        if(i!=null) {
            ok = service.deleteById(id);
        }
        return ok;
    }

    public Long countInventarioProducto() {
        return service.count();
    }

}
