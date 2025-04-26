package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.configuracion.input.NotificacionPushInput;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class NotificacionPushGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private InicioSesionService inicioSesionService;
    @Autowired
    private PushNotificationService pushNotificationService;

    public Boolean requestPushNotification(NotificacionPushInput notificacionPushInput) {
        try {
            Usuario usuario = usuarioService.findByPersonaId(notificacionPushInput.getPersonaId());
            Page<InicioSesion> inicioSesionPage = inicioSesionService.findByUsuarioIdAndHoraFinIsNul(usuario.getId(), Long.valueOf(0), PageRequest.of(0, 1));
            for (InicioSesion inicioSesion : inicioSesionPage.getContent()) {
                if (inicioSesion.getToken() != null) {
                    PushNotificationRequest pNr = new PushNotificationRequest();
                    pNr.setTitle(notificacionPushInput.getTitulo());
                    pNr.setMessage(notificacionPushInput.getMensaje());
                    pNr.setToken(inicioSesion.getToken());
                    pNr.setData(notificacionPushInput.getData() != null ? notificacionPushInput.getData() : "/");
                    pushNotificationService.sendPushNotificationToToken(pNr);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }
}
