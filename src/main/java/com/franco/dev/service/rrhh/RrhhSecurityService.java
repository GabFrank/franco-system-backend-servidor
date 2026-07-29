package com.franco.dev.service.rrhh;

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
 * Control de acceso por rol **específico de RRHH**, self-contained: resuelve el
 * usuario autenticado desde el principal del SecurityContext (el nickname, que sí
 * se setea) y lee sus roles desde la DB (`personas.usuario_role`). NO depende del
 * marshaling de roles JWT→Authentication (que está roto a nivel sistema, ver issue
 * #177) ni toca `com.franco.dev.security`.
 *
 * Bypass de superusuario: rol "ADMIN" o el nickname "ADMIN" (cuenta superusuario
 * pelada, sin filas en usuario_role). Los demás usuarios necesitan el rol RRHH
 * correspondiente para cada acción sensible.
 */
@Service
public class RrhhSecurityService {

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;

    public static final String ADMIN = "ADMIN";
    public static final String VER = "RRHH VER";
    public static final String GESTIONAR = "RRHH GESTIONAR";
    public static final String LIQUIDAR = "RRHH LIQUIDAR";
    public static final String APROBAR = "RRHH APROBAR";
    public static final String PAGAR = "RRHH PAGAR";
    public static final String CONFIG = "RRHH CONFIG";

    /** Todos los roles RRHH: cualquiera habilita la lectura. */
    public static final String[] TODOS = {VER, GESTIONAR, LIQUIDAR, APROBAR, PAGAR, CONFIG};

    /** El usuario autenticado (por nickname del SecurityContext), o null. */
    public Usuario currentUsuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return usuarioService.findByNickname(auth.getName()).orElse(null);
    }

    private String currentNickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /** Nombres de rol del usuario autenticado (upper/trim), o vacío. */
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

    /** Lanza excepción si el usuario no tiene ninguno de los roles (ni es superusuario). */
    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new GraphQLException("No autorizado: se requiere el rol "
                    + String.join(" o ", roles) + " para esta acción.");
        }
    }

    /** Lectura de datos RRHH: exige cualquier rol RRHH (o superusuario). */
    public void requireVer() {
        if (!hasAnyRole(TODOS)) {
            throw new GraphQLException("No autorizado: se requiere un rol de RRHH para ver estos datos.");
        }
    }
}
