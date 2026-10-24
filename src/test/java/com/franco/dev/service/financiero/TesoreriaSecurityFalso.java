package com.franco.dev.service.financiero;

import com.franco.dev.domain.personas.Usuario;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Sesión fija para tests de resolvers: usa los require* reales con el usuario y los roles que se
 * le pasan, sin SecurityContext ni base.
 */
public class TesoreriaSecurityFalso extends TesoreriaSecurityService {

    private final Usuario usuario;
    private final Set<String> roles;

    public TesoreriaSecurityFalso(Long usuarioId, String... roles) {
        if (usuarioId == null) {
            this.usuario = null;
        } else {
            this.usuario = new Usuario();
            this.usuario.setId(usuarioId);
        }
        this.roles = new HashSet<>(Arrays.asList(roles));
    }

    @Override
    public Usuario currentUsuario() { return usuario; }

    @Override
    public Set<String> currentRoles() { return roles; }

    @Override
    public boolean esSuperusuario() { return roles.contains(ADMIN); }

    @Override
    public boolean hasAnyRole(String... pedidos) {
        if (roles.contains(ADMIN)) return true;
        for (String r : pedidos) if (roles.contains(r)) return true;
        return false;
    }
}
