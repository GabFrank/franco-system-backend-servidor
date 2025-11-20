package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.service.configuracion.NotificacionUsuarioService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;

@Component
public class NotificacionUsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotificacionUsuarioService notificacionUsuarioService;

    public Boolean marcarNotificacionLeida(Long notificacionUsuarioId) {
        try {
            return notificacionUsuarioService.marcarComoLeida(notificacionUsuarioId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Boolean registrarInteraccionNotificacion(Long notificacionUsuarioId, String accion) {
        try {
            return notificacionUsuarioService.registrarInteraccion(notificacionUsuarioId, accion);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public NotificacionUsuarioPage getNotificacionesUsuario(Long usuarioId, String tokenFcm, Boolean leidas,
            Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "creadoEn"));
        Page<NotificacionUsuario> result = notificacionUsuarioService.findByUsuarioId(usuarioId, tokenFcm, pageable);
        List<NotificacionUsuario> content = (leidas == null) ? result.getContent()
                : result.getContent().stream()
                        .filter(nu -> Boolean.TRUE.equals(nu.getLeida()) == leidas)
                        .toList();

        return new NotificacionUsuarioPage(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }
}
