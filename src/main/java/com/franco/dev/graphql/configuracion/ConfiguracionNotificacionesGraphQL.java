package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.configuracion.NotificacionTipoEstado;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.configuracion.model.ConfiguracionNotificacionDto;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import com.franco.dev.service.configuracion.NotificacionTipoEstadoService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ConfiguracionNotificacionesGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotificacionPreferenciaService notificacionPreferenciaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private NotificacionTipoEstadoService tipoEstadoService;

    public List<ConfiguracionNotificacionDto> misConfiguracionesNotificacion() {
        Usuario usuario = getUsuarioActual();
        if (usuario == null) {
            return Collections.emptyList();
        }
        return notificacionPreferenciaService.obtenerConfiguracionesPorUsuario(usuario);
    }

    public Boolean actualizarPreferenciaNotificacion(String tipoNotificacion, Boolean habilitado) {
        Usuario usuario = getUsuarioActual();
        if (usuario == null) {
            return false;
        }
        return notificacionPreferenciaService.actualizarPreferencia(usuario, tipoNotificacion, habilitado);
    }

    /**
     * Que tipos estan suspendidos y por que.
     *
     * <p>
     * Los que no tienen fila estan activos: la lista muestra lo que alguien
     * decidio apagar, no el universo de tipos.
     */
    public List<NotificacionTipoEstado> estadosNotificacion() {
        exigirAdmin();
        return tipoEstadoService.listar();
    }

    /** Prende o apaga un tipo entero, para todos. */
    public NotificacionTipoEstado cambiarEstadoNotificacion(String tipoNotificacion, Boolean activo, String motivo) {
        exigirAdmin();
        return tipoEstadoService.cambiar(tipoNotificacion, activo, motivo);
    }

    /**
     * Solo ADMIN prende o apaga notificaciones.
     *
     * <p>
     * ⚠️ <b>No se usa {@code @AdminSecured}.</b> Esa anotacion mira las
     * authorities del SecurityContext, y el marshaling de roles JWT ->
     * Authentication esta roto a nivel sistema (issue #177): llegan como
     * "[ADMIN", "SOPORTE", ... —el toString de la lista partido por comas—,
     * asi que la comparacion contra "ROLE_ADMIN" no da nunca y el metodo
     * quedaria cerrado hasta para un ADMIN real. Se resuelve desde la DB.
     */
    private void exigirAdmin() {
        Usuario usuario = getUsuarioActual();
        if (usuario == null) {
            throw new GraphQLException("Necesitas iniciar sesion para hacer eso.");
        }
        if ("ADMIN".equalsIgnoreCase(usuario.getNickname())) {
            return;
        }
        for (Role rol : roleService.findByUsuarioId(usuario.getId())) {
            if (rol.getNombre() != null && "ADMIN".equalsIgnoreCase(rol.getNombre().trim())) {
                return;
            }
        }
        throw new GraphQLException("Solo un administrador puede cambiar el estado de las notificaciones.");
    }

    private Usuario getUsuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        return usuarioService.findByNickname(username).orElse(null);
    }
}
