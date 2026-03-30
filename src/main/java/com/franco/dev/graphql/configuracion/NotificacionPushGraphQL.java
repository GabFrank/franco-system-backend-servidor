package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.configuracion.input.NotificacionPushInput;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificacionPushGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private PushNotificationService pushNotificationService;
    @Autowired
    private NotificationTemplateService notificationTemplateService;

    public Boolean requestPushNotification(NotificacionPushInput notificacionPushInput) {
        try {
            Usuario usuario = usuarioService.findByPersonaId(notificacionPushInput.getPersonaId());
            PushNotificationRequest request = notificationTemplateService.manual(
                    notificacionPushInput.getTitulo(),
                    notificacionPushInput.getMensaje(),
                    notificacionPushInput.getData(),
                    "MANUAL");
            request.setUsuarioIds(Collections.singletonList(usuario.getId()));
            pushNotificationService.sendPushNotificationToToken(request);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Envía una notificación personalizada a usuarios específicos o a todos los
     * usuarios activos
     * 
     * @param titulo      Título de la notificación
     * @param mensaje     Contenido del mensaje de la notificación
     * @param tipoEnvio   Tipo de envío: "TODOS" o "ESPECIFICOS"
     * @param usuariosIds Lista de IDs de usuarios (requerido si tipoEnvio es
     *                    "ESPECIFICOS")
     * @return true si se envió exitosamente, false en caso contrario
     */
    public Boolean enviarNotificacionPersonalizada(String titulo, String mensaje, String tipoEnvio,
            java.util.List<Integer> usuariosIds) {
        try {
            java.util.List<Long> usuariosIdsLong = null;
            if (usuariosIds != null) {
                usuariosIdsLong = usuariosIds.stream()
                        .map(Integer::longValue)
                        .collect(java.util.stream.Collectors.toList());
            }

            return pushNotificationService.enviarNotificacionPersonalizada(titulo, mensaje, tipoEnvio, usuariosIdsLong);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}