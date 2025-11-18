package com.franco.dev.fmc.controller;

import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.model.PushNotificationResponse;
import com.franco.dev.fmc.service.PushNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PushNotificationController {


    private PushNotificationService pushNotificationService;

    public PushNotificationController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping("/notification/token")
    public ResponseEntity<PushNotificationResponse> sendTokenNotification(@RequestBody PushNotificationRequest request) {
        pushNotificationService.sendPushNotificationToToken(request);
        return new ResponseEntity<>(new PushNotificationResponse(HttpStatus.ACCEPTED.value(),
                "Esta notificacion ahora esta en cola para envío asíncrono."), HttpStatus.ACCEPTED);
    }

}
 