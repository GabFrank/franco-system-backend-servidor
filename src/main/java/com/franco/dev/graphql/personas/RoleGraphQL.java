package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.RoleInput;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
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
public class RoleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RoleService service;

    @Autowired
    private UsuarioRoleService usuarioRoleService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Role> role(Long id) {return service.findById(id);}

    public List<Role> roles(Integer page, Integer size){
        Pageable pageable = (page==null || size == null) ? null : PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Role saveRole(RoleInput input){
        ModelMapper m = new ModelMapper();
        Role e = m.map(input, Role.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.ROLE);
        multiTenantService.compartir(null, (Role s) -> service.save(s), e);
        return e;
    }

    public Boolean deleteRole(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countRole(){
        return service.count();
    }
}
