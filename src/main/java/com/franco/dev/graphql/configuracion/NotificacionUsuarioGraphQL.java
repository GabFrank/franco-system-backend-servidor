package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.service.configuracion.NotificacionUsuarioService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;

@Component
public class NotificacionUsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotificacionUsuarioService notificacionUsuarioService;

    @Autowired
    private com.franco.dev.service.personas.UsuarioService usuarioService;

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

    public Boolean actualizarEstadoTableroNotificacion(Long notificacionUsuarioId, String estado) {
        try {
            EstadoNotificacionTablero nuevoEstado = EstadoNotificacionTablero.valueOf(estado);
            return notificacionUsuarioService.actualizarEstadoTablero(notificacionUsuarioId, nuevoEstado);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public NotificacionUsuarioPage getNotificacionesUsuario(String tokenFcm, Boolean leidas,
            Integer page, Integer size, String estadoTablero) {

        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }

        String username = authentication.getName();
        com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "creadoEn"));

        Page<NotificacionUsuario> result;

        if (estadoTablero != null && !estadoTablero.isEmpty()) {
            try {
                EstadoNotificacionTablero estado = EstadoNotificacionTablero.valueOf(estadoTablero);
                result = notificacionUsuarioService.findByUsuarioIdAndEstadoTablero(
                        usuario.getId(), tokenFcm, estado, pageable);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Estado de tablero no válido: " + estadoTablero);
            }
        } else {
            result = notificacionUsuarioService.findByUsuarioId(usuario.getId(), tokenFcm, pageable);

            if (leidas != null) {
                List<NotificacionUsuario> filteredContent = result.getContent().stream()
                        .filter(nu -> Boolean.TRUE.equals(nu.getLeida()) == leidas)
                        .collect(Collectors.toList());
                result = new PageImpl<>(
                        filteredContent,
                        pageable,
                        filteredContent.size());
            }
        }

        return new NotificacionUsuarioPage(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public Long getConteoNotificacionesNoLeidas(String tokenFcm) {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }

        String username = authentication.getName();
        com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return notificacionUsuarioService.countByFilters(
                usuario.getId(),
                tokenFcm,
                null,
                false);
    }
}
