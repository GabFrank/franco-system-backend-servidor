package com.franco.dev.fmc.service;

import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.AndroidNotification.Priority;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import com.google.gson.Gson;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FCMService {

    private static final String DEFAULT_DATA_PATH = "/";
    private static final Logger logger = LoggerFactory.getLogger(FCMService.class);
    private final Gson gson;
    @SuppressWarnings("unused")
    private final FCMInitializer fcmInitializer;

    public FCMService(Gson gson, FCMInitializer fcmInitializer) {
        this.gson = gson;
        this.fcmInitializer = fcmInitializer;
    }

    public DeliveryResult sendToToken(String token, PushNotificationRequest request) {
        try {
            Message message = baseMessageBuilder(request)
                    .setToken(token)
                    .putData("path", request.getData() != null ? request.getData() : DEFAULT_DATA_PATH)
                    .putData("title", request.getTitle())
                    .putData("message", request.getMessage())
                    .putData("type", request.getType() != null ? request.getType() : "GENERAL")
                    .build();

            FirebaseMessaging.getInstance().send(message);
            return DeliveryResult.success();
        } catch (FirebaseMessagingException ex) {
            return clasificarError(ex.getMessagingErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            return DeliveryResult.failure(ex.getMessage(), null);
        }
    }

    /**
     * Traduce el error de FCM a una decision: limpiar el token, reintentar, o
     * descartar el envio.
     *
     * Un codigo permanente clasificado como fallo generico deja el token muerto
     * en inicio_sesion reintentandose en cada notificacion, para siempre. Uno
     * transitorio clasificado como permanente descarta una notificacion valida.
     */
    static DeliveryResult clasificarError(MessagingErrorCode code, String message) {
        if (code == MessagingErrorCode.INVALID_ARGUMENT
                || code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return DeliveryResult.invalidToken(message, code);
        }
        if (code == MessagingErrorCode.THIRD_PARTY_AUTH_ERROR) {
            // La suscripcion web se creo contra otra clave VAPID y no se recupera.
            // Se avisa aparte porque si el error se vuelve masivo la causa deja de
            // ser la suscripcion y pasa a ser la credencial webpush del servidor,
            // y en ese caso esto estaria limpiando tokens sanos.
            logger.warn("FCM rechazo la suscripcion webpush ({}). Si se repite masivamente,"
                    + " revisar la clave VAPID del proyecto antes que los tokens: {}", code, message);
            return DeliveryResult.invalidToken(message, code);
        }
        if (code == MessagingErrorCode.UNAVAILABLE
                || code == MessagingErrorCode.INTERNAL
                || code == MessagingErrorCode.QUOTA_EXCEEDED) {
            return DeliveryResult.transientError(message, code);
        }
        return DeliveryResult.failure(message, code);
    }

    public DeliveryResult sendToTopic(PushNotificationRequest request) {
        try {
            Message message = baseMessageBuilder(request)
                    .setTopic(request.getTopic())
                    .putData("path", request.getData() != null ? request.getData() : DEFAULT_DATA_PATH)
                    .putData("title", request.getTitle())
                    .putData("message", request.getMessage())
                    .putData("type", request.getType() != null ? request.getType() : "GENERAL")
                    .build();
            FirebaseMessaging.getInstance().send(message);
            return DeliveryResult.success();
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode code = ex.getMessagingErrorCode();
            return DeliveryResult.failure(ex.getMessage(), code);
        }
    }

    private Message.Builder baseMessageBuilder(PushNotificationRequest request) {
        return Message.builder()
                .setApnsConfig(getApnsConfig(request.getTopic()))
                .setAndroidConfig(getAndroidConfig(request.getTopic(), request.getTitle(), request.getMessage()))
                .setWebpushConfig(getWebpushConfig(request))
                .setNotification(Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getMessage())
                        .build());
    }

    /**
     * Config del canal web.
     *
     * El destino va DOS veces y no es redundante:
     *
     * - En el `data` del mensaje, que es lo que recibe la app cuando esta
     *   abierta.
     * - Dentro del `notification`, como `onActionClick`, que es lo unico que
     *   sobrevive cuando la app esta cerrada.
     *
     * El service worker de Angular arma la notificacion copiando campos de
     * `payload.notification` y abre lo que encuentre en
     * `notification.data.onActionClick`. El `data` del mensaje es HERMANO de
     * `notification`, no hijo, asi que sin esto la notificacion aparece y
     * tocarla no hace nada: con la app cerrada ni siquiera la abre.
     *
     * `navigateLastFocusedOrOpen` reusa la pestana que ya este abierta y solo
     * abre una nueva si no hay ninguna. Con `openWindow` cada notificacion
     * dejaria otra pestana de la app.
     *
     * Esto no toca Android ni iOS: `WebpushConfig` solo lo lee el navegador.
     */
    private WebpushConfig getWebpushConfig(PushNotificationRequest request) {
        String path = request.getData() != null ? request.getData() : DEFAULT_DATA_PATH;

        Map<String, Object> alTocar = new HashMap<>();
        alTocar.put("operation", "navigateLastFocusedOrOpen");
        alTocar.put("url", path);

        Map<String, Object> acciones = new HashMap<>();
        // "default" es la clave que usa el service worker para el toque sobre
        // el cuerpo de la notificacion, por oposicion a un boton de accion.
        acciones.put("default", alTocar);

        Map<String, Object> datos = new HashMap<>();
        datos.put("onActionClick", acciones);
        datos.put("path", path);

        return WebpushConfig.builder()
                .putHeader("Urgency", "high")
                .setNotification(WebpushNotification.builder()
                        .setBody(request.getMessage())
                        .setTitle(request.getTitle())
                        .setRequireInteraction(true)
                        .putCustomData("data", datos)
                        .build())
                .build();
    }

    private AndroidConfig getAndroidConfig(String topic, String title, String body) {
        String collapseKey = topic != null ? topic : "direct-notification";
        return AndroidConfig.builder()
                .setTtl(Duration.ofHours(24).toMillis())
                .setCollapseKey(collapseKey)
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setTag(collapseKey)
                        .setSound("default")
                        .setTitle(title)
                        .setBody(body)
                        .setPriority(Priority.HIGH)
                        .setDefaultSound(true)
                        .setDefaultVibrateTimings(true)
                        .setChannelId("fcm_default_channel")
                        .build())
                .build();
    }

    private ApnsConfig getApnsConfig(String topic) {
        String apnsTopic = topic != null ? topic : "direct-notification";
        return ApnsConfig.builder()
                .setAps(Aps.builder().setCategory(apnsTopic).setThreadId(apnsTopic).build())
                .build();
    }

    private Object safeLogPayload(PushNotificationRequest request) {
        return new Object() {
            final String title = request.getTitle();
            final String type = request.getType();
            final String topic = request.getTopic();
        };
    }
}