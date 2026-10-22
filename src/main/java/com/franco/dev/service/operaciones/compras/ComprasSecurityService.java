package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Quién puede operar Compras desde el celular. Seguridad a mano, como en todo este repo (no hay
 * {@code @PreAuthorize} aplicado y {@code @AdminSecured} está roto, issue #177).
 *
 * Los roles son los mismos que muestran «Compras» en el menú del desktop
 * ({@code side-mini-variant.component.ts}): no se inventa un rol nuevo para el celular.
 */
@Service
public class ComprasSecurityService {

    public static final String ADMIN = "ADMIN";
    public static final List<String> ROLES_COMPRAS = Arrays.asList(
            "ANALISIS DE CAJA", "ANALISIS FINANCIERO", "ANALISIS CONTABLE", "VER PRECIO COSTO",
            "RECIBIR PEDIDOS", "TESORERIA VER", "TESORERIA GESTIONAR");

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private RoleService roleService;

    public Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String nick = auth != null ? auth.getName() : null;
        return nick != null ? usuarioService.findByNickname(nick).orElse(null) : null;
    }

    public boolean puedeOperar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String nick = auth != null ? auth.getName() : null;
        if (nick != null && ADMIN.equalsIgnoreCase(nick)) return true;
        Usuario u = usuarioActual();
        if (u == null) return false;
        Set<String> mios = new HashSet<>();
        for (Role r : roleService.findByUsuarioId(u.getId())) {
            if (r.getNombre() != null) mios.add(r.getNombre().trim().toUpperCase());
        }
        if (mios.contains(ADMIN)) return true;
        return ROLES_COMPRAS.stream().anyMatch(mios::contains);
    }

    public void exigirCompras() {
        if (!puedeOperar()) {
            throw new GraphQLException("No autorizado: Compras necesita uno de estos roles: "
                    + String.join(", ", ROLES_COMPRAS) + ".");
        }
    }
}
