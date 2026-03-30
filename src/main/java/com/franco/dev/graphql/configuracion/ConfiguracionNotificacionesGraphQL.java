package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.configuracion.model.ConfiguracionNotificacionDto;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import com.franco.dev.service.personas.UsuarioService;
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

    private Usuario getUsuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        return usuarioService.findByNickname(username).orElse(null);
    }
}
