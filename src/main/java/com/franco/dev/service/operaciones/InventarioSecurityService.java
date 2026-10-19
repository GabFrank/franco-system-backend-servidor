package com.franco.dev.service.operaciones;

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
 * Control de acceso por rol de las pantallas de inventario nuevas, self-contained: resuelve el
 * usuario por el nickname del SecurityContext y lee sus roles de la DB (`personas.usuario_role`).
 * No depende del marshaling de roles JWT→Authentication, roto a nivel sistema (issue #177).
 *
 * Mismo patron que {@code service.financiero.FacturacionSecurityService}. Superusuario: rol
 * "ADMIN" o el nickname "ADMIN".
 */
@Service
public class InventarioSecurityService {

    public static final String ADMIN = "ADMIN";
    public static final String VER_INVENTARIO = "VER INVENTARIO";

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;

    /** Ver el control de stock negativo. */
    public void requireVerInventario() { requireAnyRole(VER_INVENTARIO); }

    private String currentNickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private Set<String> currentRoles() {
        String nick = currentNickname();
        if (nick == null) return Collections.emptySet();
        Usuario u = usuarioService.findByNickname(nick).orElse(null);
        if (u == null) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (Role r : roleService.findByUsuarioId(u.getId())) {
            if (r.getNombre() != null) names.add(r.getNombre().trim().toUpperCase());
        }
        return names;
    }

    public boolean hasAnyRole(String... roles) {
        String nick = currentNickname();
        if (nick == null) return false;
        if (ADMIN.equalsIgnoreCase(nick)) return true;
        Set<String> mine = currentRoles();
        if (mine.contains(ADMIN)) return true;
        for (String r : roles) {
            if (r != null && mine.contains(r.trim().toUpperCase())) return true;
        }
        return false;
    }

    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new GraphQLException("No autorizado: se requiere el rol "
                    + String.join(" o ", roles) + " para esta accion.");
        }
    }
}
