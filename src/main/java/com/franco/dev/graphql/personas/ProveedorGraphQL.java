package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.ClienteUpdateInput;
import com.franco.dev.graphql.personas.input.ProveedorInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.GraphQLException;
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
public class ProveedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProveedorService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Proveedor> proveedor(Long id) {return service.findById(id);}

    public List<Proveedor> proveedores(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Proveedor> proveedorPorVendedor(Long id){
        return service.findByVendedorId(id);
    }

    public Proveedor saveProveedor(ProveedorInput input) throws GraphQLException {
        ModelMapper m = new ModelMapper();
        Proveedor e = m.map(input, Proveedor.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        try {
            e = service.save(e);
        } catch (Exception ex){
            if(ex.getMessage().contains("proveedor_un")) {
                throw new GraphQLException("Esta persona ya es un proveedor");
            }
        }
        return e;
    }

    public Boolean deleteProveedor(Long id){
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countProveedor(){
        return service.count();
    }

    public Proveedor proveedorPorPersona(Long id){
        return service.findByPersonaId(id);
    }

    public List<Proveedor> proveedorSearchByPersona(String texto) { return service.findByPersonaNombre(texto); }

}
