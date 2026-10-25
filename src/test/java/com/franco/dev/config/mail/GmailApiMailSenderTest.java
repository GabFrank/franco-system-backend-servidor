package com.franco.dev.config.mail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contra un servidor HTTP local que hace de Google: token OAuth + users.messages.send. */
class GmailApiMailSenderTest {

    private HttpServer server;
    private final AtomicInteger pedidosToken = new AtomicInteger();
    private final List<String> formulariosToken = new ArrayList<>();
    private final List<String> autorizaciones = new ArrayList<>();
    private final List<String> cuerposEnvio = new ArrayList<>();
    private volatile int estadoToken = 200;
    private volatile String respuestaToken = "{\"access_token\":\"tok-1\",\"expires_in\":3600}";
    private volatile int estadoEnvio = 200;
    private volatile String respuestaEnvio = "{\"id\":\"abc\"}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            pedidosToken.incrementAndGet();
            formulariosToken.add(leer(ex));
            responder(ex, estadoToken, respuestaToken);
        });
        server.createContext("/send", ex -> {
            autorizaciones.add(ex.getRequestHeaders().getFirst("Authorization"));
            cuerposEnvio.add(leer(ex));
            responder(ex, estadoEnvio, respuestaEnvio);
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private GmailApiMailSender sender() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GmailApiMailSender("cid", "secreto", "refresh/1", URI.create(base + "/token"), URI.create(base + "/send"));
    }

    @Test
    void enviaElMimeCompletoConAdjuntoYReusaElToken() throws Exception {
        GmailApiMailSender sender = sender();

        sender.send(mensaje(sender, "Factura 001-001-0000001"));
        sender.send(mensaje(sender, "Otra"));

        assertEquals(1, pedidosToken.get(), "el access token se reusa mientras no vence");
        assertTrue(formulariosToken.get(0).contains("grant_type=refresh_token"));
        assertTrue(formulariosToken.get(0).contains("refresh_token=refresh%2F1"));
        assertEquals("Bearer tok-1", autorizaciones.get(0));

        String raw = cuerposEnvio.get(0).replaceAll("^\\{\"raw\":\"|\"\\}$", "");
        MimeMessage recibido = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
        assertEquals("Factura 001-001-0000001", recibido.getSubject());
        assertEquals("cliente@example.com", recibido.getAllRecipients()[0].toString());
        String mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        assertTrue(mime.contains("filename=Factura.pdf"), "va el adjunto");
    }

    @Test
    void refreshTokenRevocadoDaUnMensajeClaro() {
        estadoToken = 400;
        respuestaToken = "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}";
        GmailApiMailSender sender = sender();

        MailAuthenticationException e = assertThrows(MailAuthenticationException.class,
                () -> sender.send(mensaje(sender, "x")));
        assertTrue(e.getMessage().contains("volver a autorizar"));
    }

    @Test
    void errorDeGmailLlegaConSuDetalle() {
        estadoEnvio = 400;
        respuestaEnvio = "{\"error\":{\"code\":400,\"message\":\"Invalid To header\"}}";
        GmailApiMailSender sender = sender();

        MailSendException e = assertThrows(MailSendException.class, () -> sender.send(mensaje(sender, "x")));
        assertTrue(e.getMessage().contains("Invalid To header"), e.getMessage());
    }

    @Test
    void un401PideTokenNuevoEnElSiguienteEnvio() throws Exception {
        GmailApiMailSender sender = sender();
        estadoEnvio = 401;
        assertThrows(MailSendException.class, () -> sender.send(mensaje(sender, "x")));

        estadoEnvio = 200;
        sender.send(mensaje(sender, "y"));

        assertEquals(2, pedidosToken.get());
    }

    private static MimeMessage mensaje(GmailApiMailSender sender, String asunto) throws Exception {
        MimeMessage mensaje = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
        helper.setFrom("remitente@example.com", "Empresa S.A.");
        helper.setTo("cliente@example.com");
        helper.setSubject(asunto);
        helper.setText("Adjuntamos su factura.");
        helper.addAttachment("Factura.pdf", new ByteArrayResource(new byte[]{1, 2, 3}), "application/pdf");
        return mensaje;
    }

    private static String leer(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void responder(HttpExchange ex, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(estado, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
