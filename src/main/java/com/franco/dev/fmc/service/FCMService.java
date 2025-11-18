package com.franco.dev.fmc.service;

import com.franco.dev.fmc.model.PushNotificationRequest;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class FCMService {
    private final Logger logger = LoggerFactory.getLogger(FCMService.class);

    @Async("notificationExecutor")
    public void dispatch(PushNotificationRequest request) {
        try {
            List<String> normalizedTokens = sanitizeTokens(request.getTokens());
            if (!normalizedTokens.isEmpty()) {
                request.setTokens(normalizedTokens);
            }
            if (request.getTopic() != null && !request.getTopic().isEmpty()) {
                sendMessageToTopic(request);
            } else if (!normalizedTokens.isEmpty()) {
                sendMessageToTokens(request);
            } else if (request.getToken() != null && !request.getToken().isEmpty()) {
                sendMessageToToken(request);
            } else {
                logger.warn("Push notification descartada: no se definió token ni topic. Payload: {}", request);
            }
        } catch (FirebaseMessagingException e) {
            logger.error("Error enviando notificación push", e);
            throw new IllegalStateException("No se pudo enviar la notificación push", e);
        }
    }

    private void sendMessageToToken(PushNotificationRequest request) throws FirebaseMessagingException {
        Message message = getPreconfiguredMessageToToken(request);
        String response = FirebaseMessaging.getInstance().send(message);
        logger.info("Notificación enviada a token {}. Respuesta {}", request.getToken(), response);
    }

    private void sendMessageToTokens(PushNotificationRequest request) throws FirebaseMessagingException {
        MulticastMessage message = getPreconfiguredMessageToTokens(request);
        BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
        logger.info("Notificación enviada a {} dispositivos. Éxitos: {}, Fallas: {}",
                request.getTokens().size(), response.getSuccessCount(), response.getFailureCount());
    }

    private void sendMessageToTopic(PushNotificationRequest request) throws FirebaseMessagingException {
        Message message = getPreconfiguredMessageToTopic(request);
        String response = FirebaseMessaging.getInstance().send(message);
        logger.info("Notificación enviada al tópico {}. Respuesta {}", request.getTopic(), response);
    }

    private List<String> sanitizeTokens(List<String> tokens) {
        if (tokens == null) {
            return Collections.emptyList();
        }
        return tokens.stream()
                .filter(token -> token != null && !token.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private AndroidConfig getAndroidConfig(String topic) {
        String collapseKey = topic != null ? topic : "direct-notification";
        return AndroidConfig.builder()
                .setTtl(Duration.ofMinutes(2).toMillis())
                .setCollapseKey(collapseKey)
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder().setTag(collapseKey).build())
                .build();
    }

    private ApnsConfig getApnsConfig(String topic) {
        String apnsTopic = topic != null ? topic : "direct-notification";
        return ApnsConfig.builder()
                .setAps(Aps.builder().setCategory(apnsTopic).setThreadId(apnsTopic).build())
                .build();
    }

    private Message getPreconfiguredMessageToToken(PushNotificationRequest request) {
        return baseMessageBuilder(request)
                .setToken(request.getToken())
                .putData("path", request.getData() != null ? request.getData() : "/")
                .build();
    }

    private MulticastMessage getPreconfiguredMessageToTokens(PushNotificationRequest request) {
        return MulticastMessage.builder()
                .addAllTokens(request.getTokens())
                .setAndroidConfig(getAndroidConfig(request.getTopic()))
                .setApnsConfig(getApnsConfig(request.getTopic()))
                .putData("path", request.getData() != null ? request.getData() : "/")
                .setNotification(Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getMessage())
                        .build())
                .build();
    }

    private Message getPreconfiguredMessageToTopic(PushNotificationRequest request) {
        return baseMessageBuilder(request)
                .setTopic(request.getTopic())
                .putData("path", request.getData() != null ? request.getData() : "/")
                .build();
    }

    private Message.Builder baseMessageBuilder(PushNotificationRequest request) {
        return Message.builder()
                .setApnsConfig(getApnsConfig(request.getTopic()))
                .setAndroidConfig(getAndroidConfig(request.getTopic()))
                .setNotification(Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getMessage())
                        .build());
    }
}
