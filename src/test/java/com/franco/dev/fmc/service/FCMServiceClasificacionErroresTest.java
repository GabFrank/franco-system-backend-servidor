package com.franco.dev.fmc.service;

import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.fmc.model.DeliveryResult.DeliveryOutcome;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La clasificacion decide si el token se limpia, se reintenta o se descarta.
 * Un codigo permanente mal clasificado deja el token muerto reintentandose para
 * siempre; uno transitorio mal clasificado tira envios validos a la basura.
 */
class FCMServiceClasificacionErroresTest {

    private DeliveryOutcome clasificar(MessagingErrorCode code) {
        return FCMService.clasificarError(code, "mensaje de prueba").getOutcome();
    }

    @Test
    void tokenDesregistradoSeLimpia() {
        assertEquals(DeliveryOutcome.INVALID_TOKEN, clasificar(MessagingErrorCode.UNREGISTERED));
    }

    @Test
    void tokenMalFormadoSeLimpia() {
        assertEquals(DeliveryOutcome.INVALID_TOKEN, clasificar(MessagingErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void tokenDeOtroProyectoSeLimpia() {
        assertEquals(DeliveryOutcome.INVALID_TOKEN, clasificar(MessagingErrorCode.SENDER_ID_MISMATCH),
                "el token pertenece a otro sender: no va a funcionar nunca");
    }

    @Test
    void suscripcionWebpushRechazadaSeLimpia() {
        assertEquals(DeliveryOutcome.INVALID_TOKEN, clasificar(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR),
                "la suscripcion web se creo contra otra clave VAPID: reintentarla no la arregla");
    }

    @Test
    void cuotaExcedidaSeReintenta() {
        assertEquals(DeliveryOutcome.TRANSIENT_ERROR, clasificar(MessagingErrorCode.QUOTA_EXCEEDED),
                "la cuota se recupera sola: descartar el envio pierde una notificacion valida");
    }

    @Test
    void serviciosCaidosSeReintentan() {
        assertEquals(DeliveryOutcome.TRANSIENT_ERROR, clasificar(MessagingErrorCode.UNAVAILABLE));
        assertEquals(DeliveryOutcome.TRANSIENT_ERROR, clasificar(MessagingErrorCode.INTERNAL));
    }

    @Test
    void errorDesconocidoNoLimpiaElToken() {
        assertEquals(DeliveryOutcome.FAILURE, clasificar(null),
                "sin codigo no hay evidencia de que el token este muerto");
    }

    @Test
    void elCodigoDeErrorViajaEnElResultado() {
        DeliveryResult resultado = FCMService.clasificarError(MessagingErrorCode.QUOTA_EXCEEDED, "cuota");
        assertEquals(MessagingErrorCode.QUOTA_EXCEEDED, resultado.getErrorCode(),
                "el codigo tiene que sobrevivir para poder persistirlo y alertar");
    }
}
