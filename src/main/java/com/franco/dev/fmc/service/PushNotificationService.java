package com.franco.dev.fmc.service;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.domain.configuracion.NotificacionEnvioLog;

import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacion;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.repository.configuracion.NotificacionDestinatarioRepository;
import com.franco.dev.repository.configuracion.NotificacionEnvioLogRepository;
import com.franco.dev.repository.configuracion.NotificacionRepository;

import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.configuracion.NotificacionPreferenciaService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.validation.Valid;
import javax.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class PushNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushNotificationService.class);
    private final NotificacionRepository notificacionRepository;

    private final NotificacionDestinatarioRepository notificacionDestinatarioRepository;
    private final NotificacionEnvioLogRepository notificacionEnvioLogRepository;
    private final NotificationDispatchService dispatchService;
    private final InicioSesionService inicioSesionService;
    private final FCMService fcmService;

    private final NotificacionPreferenciaService preferenciaService;

    @PersistenceContext
    private EntityManager entityManager;

    public PushNotificationService(
            NotificacionRepository notificacionRepository,
            NotificacionDestinatarioRepository notificacionDestinatarioRepository,
            NotificacionEnvioLogRepository notificacionEnvioLogRepository,
            NotificationDispatchService dispatchService,
            InicioSesionService inicioSesionService,
            FCMService fcmService,
            com.franco.dev.service.configuracion.NotificacionPreferenciaService preferenciaService) {
        this.notificacionRepository = notificacionRepository;
        this.notificacionDestinatarioRepository = notificacionDestinatarioRepository;
        this.notificacionEnvioLogRepository = notificacionEnvioLogRepository;
        this.dispatchService = dispatchService;
        this.inicioSesionService = inicioSesionService;
        this.fcmService = fcmService;
        this.preferenciaService = preferenciaService;
    }

    public void sendPushNotificationToToken(@Valid PushNotificationRequest request) {
        enqueue(request);
    }

    public void sendPushNotificationToTopic(@Valid PushNotificationRequest request) {
        if (!request.hasTopic()) {
            throw new ValidationException("Debe definir el topic para enviar la notificación");
        }
        DeliveryResult result = fcmService.sendToTopic(request);
    }

    private void enqueue(PushNotificationRequest request) {
        if (!request.hasTopic() && !request.hasDirectTokens() && !request.hasUsuarios()) {
            throw new ValidationException("Debe proveer al menos un token, tópico o usuario destino");
        }
        if (request.hasTopic()) {
            sendPushNotificationToTopic(request);
            return;
        }
        Notificacion notificacion = buildNotification(request);
        List<Target> targets = resolveTargets(request);

        if (targets.isEmpty()) {
            notificacion.setEstado(EstadoNotificacion.CANCELADA);
            notificacion.setUltimoError("No existen tokens activos o destinatarios válidos para la solicitud");
            notificacionRepository.save(notificacion);
            return;
        }
        notificacionRepository.save(notificacion);

        Map<Long, Usuario> usuariosMap = new LinkedHashMap<>();
        for (Target target : targets) {
            if (target.getUsuarioId() != null && !usuariosMap.containsKey(target.getUsuarioId())) {
                Usuario usuario = entityManager.getReference(Usuario.class, target.getUsuarioId());
                usuariosMap.put(target.getUsuarioId(), usuario);
            }
        }
        List<NotificacionDestinatario> destinatarios = usuariosMap.values().stream()
                .map(usuario -> buildNotificacionDestinatario(notificacion, usuario))
                .collect(Collectors.toList());
        notificacionDestinatarioRepository.saveAll(destinatarios);
        List<NotificacionEnvioLog> logs = targets.stream()
                .map(target -> buildNotificacionEnvioLog(notificacion, target))
                .collect(Collectors.toList());
        notificacionEnvioLogRepository.saveAll(logs);

        dispatchService.dispatchAsync();
    }

    private Notificacion buildNotification(PushNotificationRequest request) {
        Notificacion notificacion = new Notificacion();
        notificacion.setTitulo(request.getTitle());
        notificacion.setMensaje(request.getMessage());
        notificacion.setData(request.getData());
        notificacion.setTipo(request.getType() != null ? request.getType() : "GENERAL");
        notificacion.setEstado(EstadoNotificacion.ACTIVA);
        notificacion.setEstadoTablero(EstadoNotificacionTablero.POR_VERIFICAR);
        notificacion.setIntentosEnvio(0);
        return notificacion;
    }

    private List<Target> resolveTargets(PushNotificationRequest request) {
        Set<String> dedup = new LinkedHashSet<>();
        List<Target> targets = new ArrayList<>();
        String tipoNotificacion = request.getType() != null ? request.getType() : "GENERAL";

        if (request.hasUsuarios()) {
            List<com.franco.dev.domain.configuracion.InicioSesion> sesiones = inicioSesionService
                    .findSessionsWithValidTokensByUsuarioIds(request.getUsuarioIds());

            List<Long> userIds = sesiones.stream()
                    .map(s -> s.getUsuario() != null ? s.getUsuario().getId() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            Set<Long> blockedUsers = preferenciaService.getUsuariosConNotificacionDeshabilitada(tipoNotificacion,
                    userIds);

            sesiones.forEach(session -> {
                String token = session.getToken();
                Long usuarioId = session.getUsuario() != null ? session.getUsuario().getId() : null;

                if (usuarioId != null) {
                    if (!blockedUsers.contains(usuarioId)) {
                        if (token != null && dedup.add(token)) {
                            targets.add(new Target(usuarioId, token));
                        }
                    }
                } else if (token != null && dedup.add(token)) {
                    targets.add(new Target(null, token));
                }
            });
        }

        if (request.hasDirectTokens()) {
            List<String> directTokens = new ArrayList<>();
            if (request.getToken() != null) {
                directTokens.add(request.getToken());
            }
            if (request.getTokens() != null) {
                directTokens.addAll(request.getTokens());
            }
            directTokens.stream()
                    .filter(Objects::nonNull)
                    .filter(token -> token != null && !token.trim().isEmpty())
                    .filter(dedup::add)
                    .forEach(token -> targets.add(new Target(null, token)));
        }
        return targets;
    }

    /**
     * Envía una notificación tipo push utilizando un tipo de notificación
     * específico (para integración con preferencias), a todos los usuarios con
     * sesión activa.
     */
    public void enviarAlertaTipo(String tipoNotificacion, String titulo, String mensaje, String data) {
        if (titulo == null || titulo.trim().isEmpty()) {
            LOGGER.warn("El título no puede estar vacío");
            return;
        }
        if (mensaje == null || mensaje.trim().isEmpty()) {
            LOGGER.warn("El mensaje no puede estar vacío");
            return;
        }
        if (tipoNotificacion == null || tipoNotificacion.trim().isEmpty()) {
            tipoNotificacion = "GENERAL";
        }

        List<com.franco.dev.domain.configuracion.InicioSesion> sesionesActivas = inicioSesionService
                .findSessionsWithValidTokens();
        List<Long> destinatariosIds = sesionesActivas.stream()
                .filter(s -> s.getUsuario() != null)
                .map(s -> s.getUsuario().getId())
                .distinct()
                .collect(Collectors.toList());

        if (destinatariosIds.isEmpty()) {
            LOGGER.warn("No hay usuarios activos para enviar alerta tipo {}", tipoNotificacion);
            return;
        }

        PushNotificationRequest request = new PushNotificationRequest();
        request.setTitle(titulo);
        request.setMessage(mensaje);
        request.setType(tipoNotificacion);
        request.setUsuarioIds(destinatariosIds);
        if (data != null && !data.trim().isEmpty()) {
            request.setData(data);
        }

        sendPushNotificationToToken(request);
    }

    private NotificacionDestinatario buildNotificacionDestinatario(Notificacion notificacion, Usuario usuario) {
        NotificacionDestinatario entity = new NotificacionDestinatario();
        entity.setNotificacion(notificacion);
        entity.setUsuario(usuario);
        entity.setLeida(false);
        return entity;
    }

    private NotificacionEnvioLog buildNotificacionEnvioLog(Notificacion notificacion, Target target) {
        NotificacionEnvioLog log = new NotificacionEnvioLog();
        log.setNotificacion(notificacion);
        if (target.getUsuarioId() != null) {
            Usuario usuario = entityManager.getReference(Usuario.class, target.getUsuarioId());
            log.setUsuario(usuario);
        }
        log.setTokenFcm(target.getToken());
        log.setEstadoEnvio(EstadoEnvio.PENDIENTE);
        return log;
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
            List<Long> usuariosIds) {
        return enviarNotificacionPersonalizada(titulo, mensaje, tipoEnvio, usuariosIds, null);
    }

    public Boolean enviarNotificacionPersonalizada(String titulo, String mensaje, String tipoEnvio,
            List<Long> usuariosIds, String data) {
        try {
            if (titulo == null || titulo.trim().isEmpty()) {
                LOGGER.warn("El título no puede estar vacío");
                return false;
            }

            if (mensaje == null || mensaje.trim().isEmpty()) {
                LOGGER.warn("El mensaje no puede estar vacío");
                return false;
            }

            if (tipoEnvio == null || tipoEnvio.trim().isEmpty()) {
                LOGGER.warn("El tipo de envío no puede estar vacío");
                return false;
            }
            tipoEnvio = tipoEnvio.toUpperCase().trim();

            if (!"TODOS".equals(tipoEnvio) && !"ESPECIFICOS".equals(tipoEnvio)) {
                LOGGER.warn("Tipo de envío inválido: {}. Debe ser 'TODOS' o 'ESPECIFICOS'", tipoEnvio);
                return false;
            }

            if ("ESPECIFICOS".equals(tipoEnvio) && (usuariosIds == null || usuariosIds.isEmpty())) {
                LOGGER.warn("Debe especificar al menos un usuario cuando tipoEnvio es 'ESPECIFICOS'");
                return false;
            }

            PushNotificationRequest request = new PushNotificationRequest();
            request.setTitle(titulo);
            request.setMessage(mensaje);
            request.setType("PERSONALIZADA");
            if (data != null && !data.trim().isEmpty()) {
                request.setData(data);
            }

            List<Long> destinatariosIds;
            if ("TODOS".equals(tipoEnvio)) {
                List<com.franco.dev.domain.configuracion.InicioSesion> sesionesActivas = inicioSesionService
                        .findSessionsWithValidTokens();
                destinatariosIds = sesionesActivas.stream()
                        .filter(s -> s.getUsuario() != null)
                        .map(s -> s.getUsuario().getId())
                        .distinct()
                        .collect(Collectors.toList());

                if (destinatariosIds.isEmpty()) {
                    LOGGER.warn("No hay usuarios activos en el sistema");
                    return false;
                }
            } else {
                destinatariosIds = usuariosIds;
            }

            request.setUsuarioIds(destinatariosIds);

            sendPushNotificationToToken(request);

            LOGGER.info("Notificación personalizada enviada exitosamente. Título: {}, Tipo: {}, Destinatarios: {}",
                    titulo, tipoEnvio, destinatariosIds.size());
            return true;

        } catch (Exception e) {
            LOGGER.error("Error al enviar notificación personalizada", e);
            return false;
        }
    }

    private static class Target {
        private final Long usuarioId;
        private final String token;

        private Target(Long usuarioId, String token) {
            this.usuarioId = usuarioId;
            this.token = token;
        }

        public Long getUsuarioId() {
            return usuarioId;
        }

        public String getToken() {
            return token;
        }
    }
}