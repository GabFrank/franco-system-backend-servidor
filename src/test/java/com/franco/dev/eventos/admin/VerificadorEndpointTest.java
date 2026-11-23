package com.franco.dev.eventos.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.eventos.EventosProperties;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VerificadorEndpointTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static EventosProperties props() {
        EventosProperties p = new EventosProperties();
        p.setHostsPermitidos(Collections.singletonList("127.0.0.1"));
        byte[] s = new byte[32];
        for (int i = 0; i < 32; i++) s[i] = (byte) i;
        p.setClavePrivada(Base64.getEncoder().encodeToString(s));
        p.setClaveId("k1");
        return p;
    }

    @Test
    void devuelveElCodigoYQuedaVerificado() {
        VerificadorEndpoint v = new VerificadorEndpoint(props(), M);
        AtomicReference<Map<String, String>> cab = new AtomicReference<>();
        v.cliente = (url, c, cuerpo) -> {
            cab.set(c);
            JsonNode n = M.readTree(cuerpo);
            assertEquals("py.frc.webhook.verificacion.v1", n.get("type").asText());
            assertEquals(7L, n.get("data").get("suscripcionId").asLong());
            return new AbstractMap.SimpleEntry<>(200, "{\"codigo\":\"" + n.get("data").get("codigo").asText() + "\"}");
        };
        VerificadorEndpoint.Resultado r = v.verificar(7L, "https://127.0.0.1:9443/x", null, null);
        assertTrue(r.ok, r.mensaje);
        assertTrue(cab.get().get("webhook-signature").startsWith("v1a,"));
        assertEquals("k1", cab.get().get("frc-key-id"));
    }

    @Test
    void sinCodigoNoVerifica() {
        VerificadorEndpoint v = new VerificadorEndpoint(props(), M);
        v.cliente = (url, c, cuerpo) -> new AbstractMap.SimpleEntry<>(200, "ok");
        VerificadorEndpoint.Resultado r = v.verificar(7L, "https://127.0.0.1:9443/x", null, null);
        assertFalse(r.ok);
        assertEquals(200, r.status);
    }

    @Test
    void respuesta4xxNoVerifica() {
        VerificadorEndpoint v = new VerificadorEndpoint(props(), M);
        v.cliente = (url, c, cuerpo) -> new AbstractMap.SimpleEntry<>(403, "frcv_x");
        VerificadorEndpoint.Resultado r = v.verificar(7L, "https://127.0.0.1:9443/x", null, null);
        assertFalse(r.ok);
        assertEquals(403, r.status);
    }

    @Test
    void urlInternaOHttpNoSeLlama() {
        VerificadorEndpoint v = new VerificadorEndpoint(props(), M);
        v.cliente = (url, c, cuerpo) -> { fail("no deberia llamar"); return null; };
        assertFalse(v.verificar(7L, "https://10.0.0.5/x", null, null).ok);
        assertFalse(v.verificar(7L, "http://127.0.0.1/x", null, null).ok);
    }

    @Test
    void cabeceraFijaSinSecretoNoLlama() {
        VerificadorEndpoint v = new VerificadorEndpoint(props(), M);
        v.cliente = (url, c, cuerpo) -> { fail("no deberia llamar"); return null; };
        VerificadorEndpoint.Resultado r = v.verificar(7L, "https://127.0.0.1:9443/x", "X-Loom", "loom");
        assertFalse(r.ok);
        assertTrue(r.mensaje.contains("secretos.loom"));
    }
}
