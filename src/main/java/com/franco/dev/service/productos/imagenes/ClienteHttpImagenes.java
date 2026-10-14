package com.franco.dev.service.productos.imagenes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** GET contra las fuentes de búsqueda de fotos (JSON de las APIs, HTML de Bing). */
@Component
public class ClienteHttpImagenes {

    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final HttpClient http;

    public ClienteHttpImagenes(ObjectMapper objectMapper,
                               @Value("${imagenes.sugerencias.timeout-ms:8000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5000)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public JsonNode get(String url, Map<String, String> headers) throws IOException {
        byte[] body = enviar(url, "application/json", headers);
        return body == null ? null : objectMapper.readTree(body);
    }

    public String getTexto(String url, Map<String, String> headers) throws IOException {
        byte[] body = enviar(url, "text/html,*/*;q=0.8", headers);
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    /** null si la fuente responde 404 (p. ej. Open Food Facts con un código que no conoce). */
    private byte[] enviar(String url, String accept, Map<String, String> headers) throws IOException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", accept)
                .GET();
        if (!headers.containsKey("User-Agent")) req.header("User-Agent", USER_AGENT);
        headers.forEach(req::header);
        try {
            HttpResponse<byte[]> res = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() == 404) return null;
            if (res.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + res.statusCode());
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Búsqueda interrumpida", e);
        }
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String texto(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    static Integer entero(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        if (v == null || v.isNull()) return null;
        int i = v.asInt(0);
        return i > 0 ? i : null;
    }

    static String dominio(String url) {
        if (url == null) return null;
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean esHttp(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }
}
