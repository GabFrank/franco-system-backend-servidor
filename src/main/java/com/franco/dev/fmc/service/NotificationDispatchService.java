package com.franco.dev.fmc.service;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionEnvioLog;

import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacion;
import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.repository.configuracion.NotificacionEnvioLogRepository;
import com.franco.dev.repository.configuracion.NotificacionRepository;

import com.franco.dev.service.configuracion.InicioSesionService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificacionEnvioLogRepository notificacionEnvioLogRepository;
    private final NotificacionRepository notificacionRepository;

    private final FCMService fcmService;
    private final InicioSesionService inicioSesionService;
    private final Optional<MeterRegistry> meterRegistry;

    @Value("${app.notifications.batch-size:25}")
    private int batchSize;

    @Value("${app.notifications.max-attempts:5}")
    private int maxAttempts;

    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public NotificationDispatchService(
            NotificacionEnvioLogRepository notificacionEnvioLogRepository,
            NotificacionRepository notificacionRepository,
            FCMService fcmService,
            InicioSesionService inicioSesionService,
            Optional<MeterRegistry> meterRegistry,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.notificacionEnvioLogRepository = notificacionEnvioLogRepository;
        this.notificacionRepository = notificacionRepository;
        this.fcmService = fcmService;
        this.inicioSesionService = inicioSesionService;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval:5000}")
    public void scheduledDispatch() {
        dispatchInternal();
    }

    @Async("notificationExecutor")
    public void dispatchAsync() {
        dispatchInternal();
    }

    protected void dispatchInternal() {
        List<NotificacionEnvioLog> batch = fetchAndLockBatch();
        if (batch.isEmpty()) {
            return;
        }

        for (NotificacionEnvioLog target : batch) {
            Notificacion notificacion = target.getNotificacion();
            PushNotificationRequest request = new PushNotificationRequest();
            request.setTitle(notificacion.getTitulo());
            request.setMessage(notificacion.getMensaje());
            request.setData(notificacion.getData());
            request.setType(notificacion.getTipo());

            DeliveryResult result = fcmService.sendToToken(target.getTokenFcm(), request);
            handleResult(target, notificacion, result);
            notificacionEnvioLogRepository.save(target);
            notificacionRepository.save(notificacion);
        }
    }

    private synchronized List<NotificacionEnvioLog> fetchAndLockBatch() {
        return transactionTemplate.execute(status -> {
            List<NotificacionEnvioLog> pendientes = notificacionEnvioLogRepository.findBatchByEstado(
                    EstadoEnvio.PENDIENTE, PageRequest.of(0, batchSize));

            if (pendientes.isEmpty()) {
                return java.util.Collections.emptyList();
            }

            for (NotificacionEnvioLog target : pendientes) {
                target.setEstadoEnvio(EstadoEnvio.EN_PROCESO);
            }
            return notificacionEnvioLogRepository.saveAll(pendientes);
        });
    }

    /**
     * Antepone el codigo de FCM al mensaje. Sin el codigo, mensaje_error queda
     * como texto libre y no hay forma de filtrar ni alertar por tipo de fallo.
     */
    private static String detalleError(DeliveryResult result) {
        if (result.getErrorCode() == null) {
            return result.getMessage();
        }
        return "[" + result.getErrorCode() + "] " + result.getMessage();
    }

    void handleResult(NotificacionEnvioLog target, Notificacion notificacion, DeliveryResult result) {
        LocalDateTime now = LocalDateTime.now();
        String detalle = detalleError(result);
        notificacion.setIntentosEnvio(Optional.ofNullable(notificacion.getIntentosEnvio()).orElse(0) + 1);
        int intentosDelDestino = Optional.ofNullable(target.getIntentos()).orElse(0) + 1;
        target.setIntentos(intentosDelDestino);
        switch (result.getOutcome()) {
            case SUCCESS:
                target.setEstadoEnvio(EstadoEnvio.ENVIADO);
                target.setFechaEnvio(now);
                target.setMensajeError(null);
                meter("notifications.success");
                break;
            case INVALID_TOKEN:
                target.setEstadoEnvio(EstadoEnvio.FALLO_DESTINO);
                target.setMensajeError(detalle);
                meter("notifications.invalid-token");
                inicioSesionService.clearToken(target.getTokenFcm());
                break;
            case TRANSIENT_ERROR:
                // El presupuesto es del destino, no de la notificacion: contarlo
                // en la notificacion lo repartia entre todos sus tokens y dejaba
                // a la mayoria sin ningun reintento real.
                if (intentosDelDestino >= maxAttempts) {
                    target.setEstadoEnvio(EstadoEnvio.CANCELADA);
                    target.setMensajeError(detalle);
                    meter("notifications.cancelled.max-attempts");
                } else {
                    target.setEstadoEnvio(EstadoEnvio.PENDIENTE);
                    target.setMensajeError(detalle);
                    meter("notifications.transient-error");
                }
                break;
            case FAILURE:
                target.setEstadoEnvio(EstadoEnvio.FALLO_ENVIO);
                target.setMensajeError(detalle);
                meter("notifications.failure");
                break;
        }
        notificacion.setUltimoError(detalle);
        if (EstadoEnvio.ENVIADO.equals(target.getEstadoEnvio())) {
            maybeFinalizeNotification(notificacion);
        }
    }

    private void maybeFinalizeNotification(Notificacion notificacion) {
        boolean existsPending = notificacionEnvioLogRepository.existsByNotificacionIdAndEstadoEnvioIn(
                notificacion.getId(), Arrays.asList(EstadoEnvio.PENDIENTE, EstadoEnvio.FALLO_ENVIO));
        if (!existsPending) {
            notificacion.setEstado(EstadoNotificacion.FINALIZADA);
        }
    }

    private void meter(String name) {
        meterRegistry.ifPresent(registry -> registry.counter(name).increment());
    }
}
