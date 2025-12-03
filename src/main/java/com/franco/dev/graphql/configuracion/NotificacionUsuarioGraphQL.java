package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.service.configuracion.NotificacionDestinatarioService;
import com.franco.dev.service.configuracion.NotificacionService;
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
    private NotificacionDestinatarioService notificacionDestinatarioService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private com.franco.dev.service.personas.UsuarioService usuarioService;

    // Nueva API: marcar como leída (individual por usuario)
    public Boolean marcarNotificacionLeida(Long notificacionId) {
        try {
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }
            String username = authentication.getName();
            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username).orElse(null);
            if (usuario == null) {
                return false;
            }
            return notificacionDestinatarioService.marcarComoLeida(notificacionId, usuario.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Nueva API: cambiar estado del tablero (COMPARTIDO entre todos los usuarios)
    public Boolean cambiarEstadoTableroNotificacion(Long notificacionId, String estado) {
        try {
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }
            String username = authentication.getName();
            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username).orElse(null);
            if (usuario == null) {
                return false;
            }
            EstadoNotificacionTablero nuevoEstado = EstadoNotificacionTablero.valueOf(estado);
            return notificacionService.cambiarEstadoTablero(notificacionId, nuevoEstado, usuario.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // API legacy (mantener para compatibilidad)
    @Deprecated
    public Boolean registrarInteraccionNotificacion(Long notificacionUsuarioId, String accion) {
        try {
            return notificacionUsuarioService.registrarInteraccion(notificacionUsuarioId, accion);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Deprecated
    public Boolean actualizarEstadoTableroNotificacion(Long notificacionUsuarioId, String estado) {
        try {
            EstadoNotificacionTablero nuevoEstado = EstadoNotificacionTablero.valueOf(estado);
            return notificacionUsuarioService.actualizarEstadoTablero(notificacionUsuarioId, nuevoEstado);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Nueva API: obtener notificaciones con estado compartido
    public NotificacionDestinatarioPage getNotificacionesUsuario(Boolean leidas,
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
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "notificacion.creadoEn"));

        Page<NotificacionDestinatario> result;

        if (estadoTablero != null && !estadoTablero.isEmpty()) {
            try {
                EstadoNotificacionTablero estado = EstadoNotificacionTablero.valueOf(estadoTablero);
                result = notificacionDestinatarioService.findByUsuarioIdAndFilters(
                        usuario.getId(), estado, leidas, pageable);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Estado de tablero no válido: " + estadoTablero);
            }
        } else {
            result = notificacionDestinatarioService.findByUsuarioId(usuario.getId(), leidas, pageable);
        }

        return new NotificacionDestinatarioPage(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    // API legacy (mantener para compatibilidad con frontend antiguo)
    @Deprecated
    public NotificacionUsuarioPage getNotificacionesUsuarioLegacy(String tokenFcm, Boolean leidas,
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

    public Long getConteoNotificacionesNoLeidas() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }

        String username = authentication.getName();
        com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return notificacionDestinatarioService.countNoLeidasByUsuarioId(usuario.getId());
    }
}
