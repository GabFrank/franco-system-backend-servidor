package com.franco.dev.eventos.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.franco.dev.eventos.DestinoSeguro;
import com.franco.dev.eventos.EventosProperties;
import com.franco.dev.eventos.FirmaWebhook;
import com.franco.dev.eventos.ResolverSeguro;
import com.franco.dev.eventos.Ulid;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verificacion del endpoint de una suscripcion (srv #587, C1): manda py.frc.webhook.verificacion con un
 * codigo al azar y exige que el receptor lo devuelva en el cuerpo de la respuesta 2xx. Mismo cliente
 * seguro que el envio: IP validada al conectar, sin redirecciones y con timeout.
 */
@Component
public class VerificadorEndpoint {

    public static final class Resultado {
        public final boolean ok;
        public final Integer status;
        public final String mensaje;

        Resultado(boolean ok, Integer status, String mensaje) {
            this.ok = ok;
            this.status = status;
            this.mensaje = mensaje;
        }
    }

    /** Para los tests: el POST que devuelve status y cuerpo. */
    public interface Cliente {
        Map.Entry<Integer, String> post(String url, Map<String, String> cabeceras, String cuerpo) throws Exception;
    }

    private static final SecureRandom AZAR = new SecureRandom();
    private static final int MAX_CUERPO = 4096;

    private final EventosProperties props;
    private final ObjectMapper mapper;
    Cliente cliente;

    public VerificadorEndpoint(EventosProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.cliente = netty(props);
    }

    public Resultado verificar(Long suscripcionId, String url, String cabeceraNombre, String secretoRef) {
        String error = DestinoSeguro.validar(url, props.getHostsPermitidos());
        if (error != null) return new Resultado(false, null, error);
        String codigo = codigo();
        String id = "evt_0_" + Ulid.nuevo();
        ObjectNode n = mapper.createObjectNode();
        n.put("specversion", "1.0");
        n.put("id", id);
        n.put("source", "frc://" + props.fuente());
        n.put("type", "py.frc.webhook.verificacion.v1");
        n.put("time", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        n.put("datacontenttype", "application/json");
        ObjectNode data = n.putObject("data");
        data.put("suscripcionId", suscripcionId);
        data.put("codigo", codigo);
        data.put("instruccion", "Responde 2xx con este codigo en el cuerpo para activar la suscripcion");
        String cuerpo = n.toString();

        Map<String, String> cab = new LinkedHashMap<>();
        cab.put("Content-Type", "application/cloudevents+json");
        long ts = System.currentTimeMillis() / 1000;
        cab.put("webhook-id", id);
        cab.put("webhook-timestamp", String.valueOf(ts));
        try {
            String semilla = props.getClavePrivada();
            if (semilla != null && !semilla.trim().isEmpty()) {
                String firmas = new FirmaWebhook(semilla).firmar(id, ts, cuerpo);
                String hmac = props.getSecretosHmac().get(String.valueOf(suscripcionId));
                if (hmac != null && !hmac.trim().isEmpty()) firmas += " " + FirmaWebhook.firmarHmac(hmac, id, ts, cuerpo);
                cab.put("webhook-signature", firmas);
                if (props.getClaveId() != null) cab.put("frc-key-id", props.getClaveId());
            }
        } catch (Exception e) {
            return new Resultado(false, null, "No se pudo firmar: revisa frc.webhooks.clave-privada y el secreto HMAC");
        }
        if (cabeceraNombre != null && !cabeceraNombre.trim().isEmpty()) {
            String valor = secretoRef == null ? null : props.getSecretos().get(secretoRef);
            if (valor == null) return new Resultado(false, null, "Falta frc.webhooks.secretos." + secretoRef + " en el central");
            cab.put(cabeceraNombre.trim(), valor);
        }
        Map.Entry<Integer, String> r;
        try {
            r = cliente.post(url, cab, cuerpo);
        } catch (Exception e) {
            return new Resultado(false, null, "No se pudo conectar: " + e.getClass().getSimpleName());
        }
        int st = r.getKey();
        if (st < 200 || st > 299) return new Resultado(false, st, "El receptor respondio HTTP " + st);
        String resp = r.getValue() == null ? "" : r.getValue();
        if (!resp.contains(codigo)) return new Resultado(false, st, "El receptor respondio sin devolver el codigo");
        return new Resultado(true, st, "Endpoint verificado");
    }

    static String codigo() {
        byte[] b = new byte[12];
        AZAR.nextBytes(b);
        StringBuilder sb = new StringBuilder("frcv_");
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static Cliente netty(EventosProperties props) {
        reactor.netty.http.client.HttpClient http = reactor.netty.http.client.HttpClient.create()
                .resolver(new ResolverSeguro(props.getHostsPermitidos()))
                .followRedirect(false)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.getTimeoutMs()));
        return (url, cab, cuerpo) -> {
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            return http.headers(h -> {
                        cab.forEach(h::set);
                        h.set("Content-Length", String.valueOf(bytes.length));
                    })
                    .post()
                    .uri(url)
                    .send(reactor.core.publisher.Mono.just(io.netty.buffer.Unpooled.wrappedBuffer(bytes)))
                    .responseSingle((resp, body) -> body.asByteArray().defaultIfEmpty(new byte[0]).map(b -> {
                        String s = new String(b, 0, Math.min(b.length, MAX_CUERPO), StandardCharsets.UTF_8);
                        return (Map.Entry<Integer, String>) new java.util.AbstractMap.SimpleEntry<>(resp.status().code(), s);
                    }))
                    .block(Duration.ofMillis(props.getTimeoutMs() + 1000L));
        };
    }
}
