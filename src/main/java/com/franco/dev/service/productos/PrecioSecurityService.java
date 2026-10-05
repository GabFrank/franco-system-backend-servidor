package com.franco.dev.service.productos;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Seguridad a mano de las escrituras de precios especiales, como en todo este repo: no hay
 * {@code @PreAuthorize} aplicado y {@code @AdminSecured} esta roto (issue #177). Roles con espacio,
 * los mismos que usa el desktop (ROLES.CREAR_PRECIOS / ROLES.EDITAR_PRECIOS).
 */
@Service
public class PrecioSecurityService {

    public static final String ADMIN = "ADMIN";
    public static final String CREAR = "CREAR PRECIOS";
    public static final String EDITAR = "EDITAR PRECIOS";

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;

    private String currentNickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    public Usuario currentUsuario() {
        String nick = currentNickname();
        if (nick == null) return null;
        return usuarioService.findByNickname(nick).orElse(null);
    }

    private Set<String> currentRoles() {
        Usuario u = currentUsuario();
        if (u == null) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (Role r : roleService.findByUsuarioId(u.getId())) {
            if (r.getNombre() != null) names.add(r.getNombre().trim().toUpperCase());
        }
        return names;
    }

    public boolean hasAnyRole(String... roles) {
        String nick = currentNickname();
        if (nick != null && ADMIN.equalsIgnoreCase(nick)) return true;
        Set<String> mine = currentRoles();
        if (mine.contains(ADMIN)) return true;
        for (String r : roles) {
            if (r != null && mine.contains(r.trim().toUpperCase())) return true;
        }
        return false;
    }

    /** Crear, editar o cortar un precio especial. */
    public void requireGestionar() {
        if (!hasAnyRole(CREAR, EDITAR)) {
            throw new GraphQLException("No autorizado: se requiere el rol " + CREAR + " o " + EDITAR + " para esta acción.");
        }
    }
}
