package com.franco.dev.config.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envia los correos por la API de Gmail (HTTPS, puerto 443) en vez de SMTP.
 *
 * <p>DigitalOcean bloquea la salida SMTP (25, 465 y 587) de sus droplets, asi que en produccion
 * el SMTP nunca conecta. El mensaje se arma igual que siempre ({@link MimeMessage}) y se manda
 * entero a {@code users.messages.send}; los servicios que envian correo no cambian.
 *
 * <p>Autentica con OAuth: un refresh token de la cuenta (permiso {@code gmail.send}) se canjea por
 * un access token, que se reusa hasta poco antes de vencer.
 */
public class GmailApiMailSender extends JavaMailSenderImpl {

    static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    static final String SEND_URI = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private static final Duration MARGEN_VENCIMIENTO = Duration.ofMinutes(2);

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final URI tokenUri;
    private final URI sendUri;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    private String accessToken;
    private Instant venceEn = Instant.EPOCH;

    public GmailApiMailSender(String clientId, String clientSecret, String refreshToken) {
        this(clientId, clientSecret, refreshToken, URI.create(TOKEN_URI), URI.create(SEND_URI));
    }

    GmailApiMailSender(String clientId, String clientSecret, String refreshToken, URI tokenUri, URI sendUri) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.refreshToken = refreshToken.trim();
        this.tokenUri = tokenUri;
        this.sendUri = sendUri;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        Map<Object, Exception> fallidos = new LinkedHashMap<>();
        for (int i = 0; i < mimeMessages.length; i++) {
            Object original = originalMessages != null ? originalMessages[i] : mimeMessages[i];
            try {
                enviar(mimeMessages[i]);
            } catch (MailAuthenticationException e) {
                throw e;
            } catch (Exception e) {
                fallidos.put(original, e);
            }
        }
        if (!fallidos.isEmpty()) {
            throw new MailSendException(fallidos);
        }
    }

    private void enviar(MimeMessage mensaje) throws MessagingException, IOException, InterruptedException {
        mensaje.saveChanges();
        ByteArrayOutputStream crudo = new ByteArrayOutputStream();
        mensaje.writeTo(crudo);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo.toByteArray());
        String cuerpo = json.writeValueAsString(Map.of("raw", raw));

        HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(sendUri)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() == 401) {
            // El access token se invalido antes de tiempo: se pide otro la proxima vez.
            invalidarToken();
        }
        if (respuesta.statusCode() / 100 != 2) {
            throw new MessagingException("Gmail rechazo el envio (HTTP " + respuesta.statusCode() + "): "
                    + detalleError(respuesta.body()));
        }
    }

    private synchronized String token() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(venceEn.minus(MARGEN_VENCIMIENTO))) {
            return accessToken;
        }
        String form = "grant_type=refresh_token"
                + "&client_id=" + url(clientId)
                + "&client_secret=" + url(clientSecret)
                + "&refresh_token=" + url(refreshToken);
        HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(tokenUri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(), HttpResponse.BodyHandlers.ofString());
        JsonNode nodo = leer(respuesta.body());
        if (respuesta.statusCode() / 100 != 2 || !nodo.hasNonNull("access_token")) {
            if ("invalid_grant".equals(nodo.path("error").asText())) {
                throw new MailAuthenticationException("La autorizacion de Gmail vencio o fue revocada:"
                        + " hay que volver a autorizar la cuenta que envia los correos");
            }
            throw new MailAuthenticationException("Gmail no entrego el token de acceso (HTTP "
                    + respuesta.statusCode() + "): " + detalleError(respuesta.body()));
        }
        accessToken = nodo.get("access_token").asText();
        venceEn = Instant.now().plusSeconds(nodo.path("expires_in").asLong(3600));
        return accessToken;
    }

    private synchronized void invalidarToken() {
        accessToken = null;
        venceEn = Instant.EPOCH;
    }

    /** Lo que Google explica en el cuerpo del error, sin volcar respuestas largas al log ni al usuario. */
    private String detalleError(String cuerpo) {
        JsonNode nodo = leer(cuerpo);
        String detalle = nodo.path("error").isObject()
                ? nodo.path("error").path("message").asText()
                : nodo.path("error_description").asText(nodo.path("error").asText());
        if (detalle.isEmpty()) {
            detalle = cuerpo == null ? "" : cuerpo;
        }
        return detalle.length() > 300 ? detalle.substring(0, 300) : detalle;
    }

    private JsonNode leer(String cuerpo) {
        try {
            return json.readTree(cuerpo == null || cuerpo.isEmpty() ? "{}" : cuerpo);
        } catch (IOException e) {
            return json.createObjectNode();
        }
    }

    private static String url(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
