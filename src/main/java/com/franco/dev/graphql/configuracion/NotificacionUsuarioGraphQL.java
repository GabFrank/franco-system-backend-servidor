package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.configuracion.NotificacionComentario;
import com.franco.dev.domain.configuracion.NotificacionDestinatario;

import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.service.configuracion.NotificacionComentarioService;
import com.franco.dev.service.configuracion.NotificacionDestinatarioService;
import com.franco.dev.service.configuracion.NotificacionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NotificacionUsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificacionUsuarioGraphQL.class);

    @Autowired
    private NotificacionDestinatarioService notificacionDestinatarioService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private com.franco.dev.service.personas.UsuarioService usuarioService;

    @Autowired
    private NotificacionComentarioService notificacionComentarioService;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    public Boolean marcarNotificacionLeida(Long notificacionId) {
        try {
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
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

    public Boolean marcarTodasNotificacionesLeidas() {
        try {
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return false;
            }
            String username = authentication.getName();
            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username).orElse(null);
            if (usuario == null) {
                return false;
            }
            return notificacionDestinatarioService.marcarTodasComoLeidas(usuario.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Boolean cambiarEstadoTableroNotificacion(Long notificacionId, String estado) {
        try {
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
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

    public NotificacionDestinatarioPage notificacionesUsuario(Boolean leidas,
            Integer page, Integer size, String estadoTablero, String fechaInicio, String fechaFin) {

        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new NotificacionDestinatarioPage(new java.util.ArrayList<>(), 0, 20, 0L, 0);
        }

        String username = authentication.getName();
        com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username).orElse(null);

        if (usuario == null) {
            int s = (size == null || size <= 0) ? 20 : size;
            return new NotificacionDestinatarioPage(new java.util.ArrayList<>(), 0, s, 0L, 0);
        }

        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "notificacion.creadoEn"));

        java.time.LocalDateTime fechaInicioLdt = null;
        java.time.LocalDateTime fechaFinLdt = null;

        if (fechaInicio != null && !fechaInicio.isEmpty()) {
            try {
                fechaInicioLdt = java.time.LocalDateTime.parse(fechaInicio,
                        java.time.format.DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e) {
                LOGGER.warn("Error parsing fechaInicio: " + fechaInicio, e);
            }
        }

        if (fechaFin != null && !fechaFin.isEmpty()) {
            try {
                fechaFinLdt = java.time.LocalDateTime.parse(fechaFin, java.time.format.DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e) {
                LOGGER.warn("Error parsing fechaFin: " + fechaFin, e);
            }
        }

        Page<NotificacionDestinatario> result;

        if (estadoTablero != null && !estadoTablero.isEmpty()) {
            try {
                EstadoNotificacionTablero estado = EstadoNotificacionTablero.valueOf(estadoTablero);
                result = notificacionDestinatarioService.findByUsuarioIdAndFilters(
                        usuario.getId(), estado, leidas, fechaInicioLdt, fechaFinLdt, pageable);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Estado de tablero no válido: " + estadoTablero);
            }
        } else {
            result = notificacionDestinatarioService.findByUsuarioIdAndFilters(
                    usuario.getId(), null, leidas, fechaInicioLdt, fechaFinLdt, pageable);
        }

        return new NotificacionDestinatarioPage(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public Long conteoNotificacionesNoLeidas() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return 0L;
        }

        String username = authentication.getName();
        com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username).orElse(null);
        if (usuario == null) {
            return 0L;
        }

        return notificacionDestinatarioService.countNoLeidasByUsuarioId(usuario.getId());
    }

    public List<NotificacionComentario> comentariosNotificacion(Long notificacionId) {
        try {
            return notificacionComentarioService.obtenerComentariosPorNotificacion(notificacionId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al obtener comentarios: " + e.getMessage());
        }
    }

    public Long conteoComentariosNotificacion(Long notificacionId) {
        try {
            return notificacionComentarioService.contarComentariosPorNotificacion(notificacionId);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public List<Usuario> usuariosDestinatariosNotificacion(Long notificacionId) {
        try {
            return notificacionDestinatarioService.obtenerUsuariosDestinatarios(notificacionId);
        } catch (Exception e) {
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }

    public List<Usuario> usuariosConAccesoNotificacion(Long notificacionId) {
        try {
            if (notificacionId == null) {
                LOGGER.warn("notificacionId es null");
                return new java.util.ArrayList<>();
            }
            List<Usuario> usuarios = notificacionDestinatarioService.obtenerUsuariosConAcceso(notificacionId);
            return usuarios != null ? usuarios : new java.util.ArrayList<>();
        } catch (Exception e) {
            LOGGER.error("Error al obtener usuarios con acceso para notificación {}: {}", notificacionId,
                    e.getMessage(), e);
            e.printStackTrace();
            throw new RuntimeException("Error al obtener usuarios con acceso: " + e.getMessage(), e);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public NotificacionComentario crearComentarioNotificacion(Long notificacionId, String comentario,
            Long comentarioPadreId) {
        try {
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new RuntimeException("Usuario no autenticado");
            }

            String username = authentication.getName();
            Usuario usuario = usuarioService.findByNickname(username)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            NotificacionComentario comentarioEntity = notificacionComentarioService.crearComentario(
                    notificacionId,
                    usuario.getId(),
                    comentario,
                    comentarioPadreId);

            if (pushNotificationService != null) {
                try {
                    List<String> menciones = notificacionComentarioService.extraerMenciones(comentario);

                    List<Usuario> usuariosMencionados = notificacionComentarioService
                            .buscarUsuariosMencionados(comentario);

                    if (!usuariosMencionados.isEmpty()) {
                        com.franco.dev.domain.configuracion.Notificacion notificacion = notificacionService
                                .findById(notificacionId).orElse(null);

                        String tituloNotificacion = notificacion != null ? notificacion.getTitulo() : "Notificación";
                        String nombreUsuario = usuario.getPersona() != null && usuario.getPersona().getNombre() != null
                                ? usuario.getPersona().getNombre()
                                : usuario.getNickname();

                        List<Long> usuariosIds = usuariosMencionados.stream()
                                .map(Usuario::getId)
                                .collect(java.util.stream.Collectors.toList());

                        if (!usuariosIds.isEmpty()) {
                            String mensaje = nombreUsuario + " te mencionó en un comentario sobre: "
                                    + tituloNotificacion;

                            String dataJson = "{\"notificacionId\":" + notificacionId + ",\"comentarioId\":"
                                    + comentarioEntity.getId() + "}";

                            Boolean resultado = pushNotificationService.enviarNotificacionPersonalizada(
                                    "Mencionado en comentario",
                                    mensaje,
                                    "ESPECIFICOS",
                                    usuariosIds,
                                    dataJson);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("ERROR al procesar menciones: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("DEBUG: pushNotificationService es null");
            }

            return comentarioEntity;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al crear comentario: " + e.getMessage());
        }
    }
}
