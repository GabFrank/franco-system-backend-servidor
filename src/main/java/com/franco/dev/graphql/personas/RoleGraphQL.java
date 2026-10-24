package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.RoleInput;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
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
public class RoleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RoleService service;

    @Autowired
    private TesoreriaSecurityService seg;

    @Autowired
    private UsuarioRoleService usuarioRoleService;


    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Role> role(Long id) {return service.findById(id);}

    public List<Role> roles(Integer page, Integer size){
        Pageable pageable = (page==null || size == null) ? null : PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public CustomPage<Role> rolesPaginated(String texto, Integer page, Integer size) {
        Page<Role> pageResult = service.findByNombrePaginated(texto, page, size);
        return new CustomPageImpl<>(pageResult.getContent(), pageResult.getPageable(), pageResult.getTotalElements(), null);
    }

    public Role saveRole(RoleInput input){
        seg.requireAnyRole("SOPORTE");
        ModelMapper m = new ModelMapper();
        Role e = m.map(input, Role.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deleteRole(Long id){
        seg.requireAnyRole("SOPORTE");
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countRole(){
        return service.count();
    }
}
