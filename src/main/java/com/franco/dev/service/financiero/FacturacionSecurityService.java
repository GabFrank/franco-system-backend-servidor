package com.franco.dev.service.financiero;

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
 * Control de acceso por rol de las notas electronicas (remision y credito), self-contained:
 * resuelve el usuario por el nickname del SecurityContext (lo unico que el JWT si setea) y lee sus
 * roles de la DB (`personas.usuario_role`). NO depende del marshaling de roles JWT→Authentication,
 * roto a nivel sistema (issue #177), ni toca `com.franco.dev.security`.
 *
 * Mismo patron que {@link TesoreriaSecurityService} y {@code service.rrhh.RrhhSecurityService}.
 * Bypass de superusuario: rol "ADMIN" o el nickname "ADMIN" (cuenta pelada, sin filas en
 * usuario_role). Los roles se siembran en V225.1.
 *
 * Alcance: solo las mutations y queries NUEVAS de notas. Las mutations SIFEN preexistentes siguen
 * protegidas solo por login (fuera del alcance de este trabajo, issue #177).
 */
@Service
public class FacturacionSecurityService {

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;

    public static final String ADMIN = "ADMIN";
    public static final String VER = "FACTURACION VER";
    public static final String EMITIR = "FACTURACION EMITIR";

    /** Cualquiera de los dos roles habilita la lectura: quien emite tambien ve. */
    public static final String[] TODOS = {VER, EMITIR};

    /** Ver notas de credito y de remision. */
    public void requireVer() { requireAnyRole(TODOS); }

    /**
     * Emitir o anular una nota, de remision o de credito: crear, generar y enviar, reenviar y
     * anular. Un unico rol para las dos notas y para la anulacion, por decision de Franco
     * (2026-09-18): el esquema de cuatro roles separados era mas granularidad de la que la
     * operacion necesita hoy. Quien puede emitir puede tambien anular lo que emitio.
     */
    public void requireEmitir() { requireAnyRole(EMITIR); }

    private String currentNickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /** El usuario autenticado (por nickname del SecurityContext), o null. */
    public Usuario currentUsuario() {
        String nick = currentNickname();
        if (nick == null) return null;
        return usuarioService.findByNickname(nick).orElse(null);
    }

    /** Nombres de rol del usuario autenticado (upper/trim), o vacio. */
    public Set<String> currentRoles() {
        Usuario u = currentUsuario();
        if (u == null) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (Role r : roleService.findByUsuarioId(u.getId())) {
            if (r.getNombre() != null) names.add(r.getNombre().trim().toUpperCase());
        }
        return names;
    }

    /** true si es superusuario (rol ADMIN o nickname ADMIN) o tiene alguno de los roles. */
    public boolean hasAnyRole(String... roles) {
        String nick = currentNickname();
        if (nick != null && ADMIN.equalsIgnoreCase(nick)) return true;   // superusuario pelado
        Set<String> mine = currentRoles();
        if (mine.contains(ADMIN)) return true;                            // rol ADMIN = super-rol
        if (roles != null) {
            for (String r : roles) {
                if (r != null && mine.contains(r.trim().toUpperCase())) return true;
            }
        }
        return false;
    }

    /** Lanza excepcion si el usuario no tiene ninguno de los roles (ni es superusuario). */
    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new GraphQLException("No autorizado: se requiere el rol "
                    + String.join(" o ", roles) + " para esta accion.");
        }
    }
}
