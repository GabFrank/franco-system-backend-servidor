package com.franco.dev.graphql.personas;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.personas.input.UsuarioRoleInput;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UsuarioRoleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioRoleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RoleService roleService;


    @Autowired
    private MultiTenantService multiTenantService;

    public List<UsuarioRole> usuarioRolePorUsuarioId(Long id) {
        return service.findByUserId(id);
    }

    public UsuarioRole saveUsuarioRole(UsuarioRoleInput input) {
        UsuarioRole e = new UsuarioRole();
        if (input.getId() != null) {
            e.setId(input.getId());
        }
        Usuario usuario = usuarioService.findById(input.getUserId()).orElse(null);
        e.setUser(usuario);
        e.setUsuario(usuario);
        e.setRole(roleService.findById(input.getRoleId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deleteUsuarioRole(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countUsuarioRole() {
        return service.count();
    }

}
