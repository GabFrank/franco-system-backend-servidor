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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Usuario usuario = usuarioService.findById(input.getUserId()).orElse(null);
        return guardarUsuarioRole(input, usuario);
    }

    @Transactional
    public List<UsuarioRole> saveUsuarioRoleList(List<UsuarioRoleInput> inputList) {
        List<UsuarioRole> guardados = new ArrayList<>();
        if (inputList == null || inputList.isEmpty()) {
            return guardados;
        }
        Long userId = inputList.get(0).getUserId();
        Usuario usuario = userId != null ? usuarioService.findById(userId).orElse(null) : null;
        Set<Long> rolesActuales = new HashSet<>();
        if (userId != null) {
            for (UsuarioRole ur : service.findByUserId(userId)) {
                if (ur.getRole() != null) {
                    rolesActuales.add(ur.getRole().getId());
                }
            }
        }
        for (UsuarioRoleInput input : inputList) {
            if (input.getRoleId() == null || !rolesActuales.add(input.getRoleId())) {
                continue;
            }
            guardados.add(guardarUsuarioRole(input, usuario));
        }
        return guardados;
    }

    private UsuarioRole guardarUsuarioRole(UsuarioRoleInput input, Usuario usuario) {
        UsuarioRole e = new UsuarioRole();
        if (input.getId() != null) {
            e.setId(input.getId());
        }
        e.setUser(usuario);
        e.setUsuario(usuario);
        e.setRole(roleService.findById(input.getRoleId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteUsuarioRole(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countUsuarioRole() {
        return service.count();
    }

}
