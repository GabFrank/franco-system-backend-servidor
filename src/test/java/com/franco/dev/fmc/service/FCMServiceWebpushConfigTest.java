package com.franco.dev.fmc.service;

import com.franco.dev.fmc.model.PushNotificationRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El service worker de Angular arma la notificacion copiando campos de
 * `payload.notification`. Lo que no viaja ahi no existe para el navegador: sin
 * `icon` ni `badge` muestra la campanita generica y un circulo con la inicial.
 */
class FCMServiceWebpushConfigTest {

    private JsonObject notificacion(PushNotificationRequest request) throws Exception {
        // Mismo serializador que usa el SDK al enviar: se mira lo que sale por
        // el cable, no los campos internos del builder.
        String json = GsonFactory.getDefaultInstance().toString(FCMService.getWebpushConfig(request));
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("notification");
    }

    private PushNotificationRequest request(String path) {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setTitle("Titulo");
        request.setMessage("Cuerpo");
        request.setData(path);
        return request;
    }

    @Test
    void llevaElIconoDeLaApp() throws Exception {
        assertEquals("/icons/icon-192x192.png", notificacion(request("/caja")).get("icon").getAsString());
    }

    @Test
    void llevaElBadgeMonocromo() throws Exception {
        assertEquals("/icons/badge-96x96.png", notificacion(request("/caja")).get("badge").getAsString());
    }

    @Test
    void lasRutasSonRelativasAlOrigen() throws Exception {
        // El central sirve a varios origenes (alpha, farmacia, bodega). Una URL
        // absoluta ataria el payload a uno solo; el service worker resuelve la
        // ruta contra su propio origen.
        JsonObject notificacion = notificacion(request(null));
        assertTrue(notificacion.get("icon").getAsString().startsWith("/"));
        assertTrue(notificacion.get("badge").getAsString().startsWith("/"));
    }

    @Test
    void elToqueSigueLlevandoAlDestino() throws Exception {
        JsonObject alTocar = notificacion(request("/caja")).getAsJsonObject("data")
                .getAsJsonObject("onActionClick").getAsJsonObject("default");
        assertEquals("navigateLastFocusedOrOpen", alTocar.get("operation").getAsString());
        assertEquals("/caja", alTocar.get("url").getAsString());
    }
}
