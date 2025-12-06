package com.franco.dev.graphql.configuracion;

import com.franco.dev.domain.configuracion.NotificacionComentario;
import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.service.configuracion.NotificacionComentarioService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NotificacionUsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificacionUsuarioGraphQL.class);

    @Autowired
    private NotificacionUsuarioService notificacionUsuarioService;

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

    public List<NotificacionComentario> getComentariosNotificacion(Long notificacionId) {
        try {
            return notificacionComentarioService.obtenerComentariosPorNotificacion(notificacionId);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al obtener comentarios: " + e.getMessage());
        }
    }

    public Long getConteoComentariosNotificacion(Long notificacionId) {
        try {
            return notificacionComentarioService.contarComentariosPorNotificacion(notificacionId);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public List<Usuario> getUsuariosDestinatariosNotificacion(Long notificacionId) {
        try {
            return notificacionDestinatarioService.obtenerUsuariosDestinatarios(notificacionId);
        } catch (Exception e) {
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }

    public List<Usuario> getUsuariosConAccesoNotificacion(Long notificacionId) {
        try {
            LOGGER.info("Obteniendo usuarios con acceso para notificación: {}", notificacionId);
            if (notificacionId == null) {
                LOGGER.warn("notificacionId es null");
                return new java.util.ArrayList<>();
            }
            List<Usuario> usuarios = notificacionDestinatarioService.obtenerUsuariosConAcceso(notificacionId);
            LOGGER.info("Usuarios encontrados: {}", usuarios != null ? usuarios.size() : 0);
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
                    System.out
                            .println("DEBUG: Menciones encontradas en comentario: " + comentario + " -> " + menciones);

                    List<Usuario> usuariosMencionados = notificacionComentarioService
                            .buscarUsuariosMencionados(comentario);
                    System.out.println("DEBUG: Usuarios encontrados: " + usuariosMencionados.size());

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

                        System.out.println("DEBUG: IDs de usuarios a notificar: " + usuariosIds);

                        if (!usuariosIds.isEmpty()) {
                            String mensaje = nombreUsuario + " te mencionó en un comentario sobre: "
                                    + tituloNotificacion;

                            System.out.println(
                                    "DEBUG: Enviando notificación push - Título: Mencionado en comentario, Mensaje: "
                                            + mensaje);

                            Boolean resultado = pushNotificationService.enviarNotificacionPersonalizada(
                                    "Mencionado en comentario",
                                    mensaje,
                                    "ESPECIFICOS",
                                    usuariosIds);

                            System.out.println("DEBUG: Resultado del envío: " + resultado);
                        }
                    } else {
                        System.out.println("DEBUG: No se encontraron usuarios mencionados");
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
