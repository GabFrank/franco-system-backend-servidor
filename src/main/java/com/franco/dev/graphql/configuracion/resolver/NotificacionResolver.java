package com.franco.dev.graphql.configuracion.resolver;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.service.configuracion.NotificacionComentarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificacionResolver implements GraphQLResolver<Notificacion> {

    @Autowired
    private NotificacionComentarioService notificacionComentarioService;

    /**
     * Resolver para el campo conteoComentarios del tipo Notificacion
     * GraphQL automáticamente llama a este método cuando se solicita el campo
     */
    public Integer conteoComentarios(Notificacion notificacion) {
        if (notificacion == null || notificacion.getId() == null) {
            return 0;
        }
        try {
            Long conteo = notificacionComentarioService.contarComentariosPorNotificacion(notificacion.getId());
            return conteo != null ? conteo.intValue() : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}

