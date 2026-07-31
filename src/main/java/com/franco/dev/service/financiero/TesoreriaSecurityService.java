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
 * Control de acceso por rol **específico de Tesorería** (caja virtual / caja
 * mayor), self-contained: resuelve el usuario autenticado desde el principal del
 * SecurityContext (el nickname, que sí se setea) y lee sus roles desde la DB
 * (`personas.usuario_role`). NO depende del marshaling de roles JWT→Authentication
 * (roto a nivel sistema, ver issue #177) ni toca `com.franco.dev.security`.
 *
 * Mismo patrón que {@code service.rrhh.RrhhSecurityService}. Bypass de
 * superusuario: rol "ADMIN" o el nickname "ADMIN" (cuenta superusuario pelada, sin
 * filas en usuario_role). Los demás usuarios necesitan el rol de tesorería
 * correspondiente para cada acción sensible.
 */
@Service
public class TesoreriaSecurityService {

    @Autowired private UsuarioService usuarioService;
    @Autowired private RoleService roleService;

    public static final String ADMIN = "ADMIN";
    public static final String VER = "TESORERIA VER";
    public static final String GESTIONAR = "TESORERIA GESTIONAR";
    /** Permiso dedicado para cobrar cuentas por cobrar (CPC). */
    public static final String CPC_COBRAR = "TESORERIA CPC COBRAR";
    /** Permiso dedicado para pagar cuentas por pagar / solicitudes (CPP). */
    public static final String CPP_PAGAR = "TESORERIA CPP PAGAR";

    /** Cualquier rol de tesorería habilita la lectura. */
    public static final String[] TODOS = {VER, GESTIONAR, CPC_COBRAR, CPP_PAGAR};

    /** Cobrar CPC: permiso dedicado, o GESTIONAR, o superusuario. */
    public void requireCobrarCpc() { requireAnyRole(CPC_COBRAR, GESTIONAR); }

    /** Pagar CPP: permiso dedicado, o GESTIONAR, o superusuario. */
    public void requirePagarCpp() { requireAnyRole(CPP_PAGAR, GESTIONAR); }

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

    /** Gestión de tesorería (crear/mover/transferir/borrar caja): exige GESTIONAR. */
    public void requireGestionar() {
        requireAnyRole(GESTIONAR);
    }

    /** Lectura de datos de tesorería: exige cualquier rol de tesorería (o superusuario). */
    public void requireVer() {
        if (!hasAnyRole(TODOS)) {
            throw new GraphQLException("No autorizado: se requiere un rol de Tesorería para ver estos datos.");
        }
    }
}
