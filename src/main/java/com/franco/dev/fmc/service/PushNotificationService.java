package com.franco.dev.fmc.service;

import com.franco.dev.fmc.config.PushNotificationConfig;
import com.franco.dev.fmc.model.PushNotificationRequest;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushNotificationService.class);
    private final RabbitTemplate pushNotificationRabbitTemplate;

    public PushNotificationService(
            @Qualifier("pushNotificationRabbitTemplate") RabbitTemplate pushNotificationRabbitTemplate) {
        this.pushNotificationRabbitTemplate = pushNotificationRabbitTemplate;
    }

    public void sendPushNotificationToToken(PushNotificationRequest request) {
        if (request == null) {
            LOGGER.warn("Solicitud de notificación nula, se ignora");
            return;
        }
        if ((request.getTokens() == null || request.getTokens().isEmpty()) && request.getToken() != null) {
            request.setTokens(Collections.singletonList(request.getToken()));
        }
        enqueue(request);
    }

    public void sendPushNotificationToTopic(PushNotificationRequest request) {
        if (request == null) {
            LOGGER.warn("Solicitud de notificación nula, se ignora");
            return;
        }
        if (request.getTopic() == null || request.getTopic().isEmpty()) {
            LOGGER.warn("No se definió topic para la notificación {}", request);
            return;
        }
        enqueue(request);
    }

    private void enqueue(PushNotificationRequest request) {
        pushNotificationRabbitTemplate.convertAndSend(PushNotificationConfig.PUSH_NOTIFICATION_QUEUE, request);
        LOGGER.debug("Notificación encolada para envío asíncrono {}", request);
    }
}