package com.franco.dev.fmc.service;

import com.franco.dev.fmc.config.PushNotificationConfig;
import com.franco.dev.fmc.model.PushNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushNotificationListener.class);
    private final FCMService fcmService;

    public PushNotificationListener(FCMService fcmService) {
        this.fcmService = fcmService;
    }

    @RabbitListener(queues = PushNotificationConfig.PUSH_NOTIFICATION_QUEUE, concurrency = "2-6")
    public void consume(PushNotificationRequest request) {
        LOGGER.debug("Recibida notificación push para procesar: {}", request);
        fcmService.dispatch(request);
    }
}


