package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.*;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper de cotizaciones de mercado desde nortecambios.com.py.
 * Replica el protocolo Livewire 3: GET home + POST /livewire/update con setActiveBranch(8).
 * Usa HttpsURLConnection directamente (no RestTemplate) para control total de SSL y headers.
 */
@Service
public class NorteCambiosScraper {

    private static final Logger log = LoggerFactory.getLogger(NorteCambiosScraper.class);

    private static final String BASE_URL = "https://www.nortecambios.com.py/";
    private static final String LIVEWIRE_URL = "https://www.nortecambios.com.py/livewire/update";
    private static final int BRANCH_ID = 8; // Salto del Guaira

    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern CSRF_PATTERN = Pattern.compile("data-csrf=\"([^\"]+)\"");
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("wire:snapshot=\"([^\"]+)\"");
    private static final Pattern RATE_PATTERN = Pattern.compile("Compra:\\s*([\\d.,]+).*?Venta:\\s*([\\d.,]+)");

    private final ObjectMapper objectMapper;
    private final SSLSocketFactory sslSocketFactory;
    private final HostnameVerifier hostnameVerifier;

    @Autowired
    public NorteCambiosScraper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.sslSocketFactory = createTrustAllSocketFactory();
        this.hostnameVerifier = (hostname, session) -> true;
    }

    private static SSLSocketFactory createTrustAllSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAllCerts, new java.security.SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            log.error("NorteCambiosScraper: no se pudo crear SSLSocketFactory trust-all", e);
            return null;
        }
    }

    /**
     * Obtiene cotizaciones de mercado de nortecambios.com.py (sucursal Salto del Guaira).
     *
     * @return Map con claves "DOLAR", "REAL", "PESO ARG" y valores double[]{ventaMercado, compraMercado}.
     *         ventaMercado = nortecambios "Compra" (tasa baja, lo que recibimos vendiendo divisa).
     *         compraMercado = nortecambios "Venta" (tasa alta, lo que pagamos comprando para proveedores).
     *         Retorna mapa vacio si falla cualquier paso.
     */
    public Map<String, double[]> fetchRates() {
        try {
            // Paso 1: GET home — extraer CSRF, snapshot, session cookie
            HttpsURLConnection getConn = openConnection(BASE_URL);
            getConn.setRequestMethod("GET");
            getConn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            getConn.setRequestProperty("Accept-Language", "es-PY,es;q=0.9");
            getConn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);

            int getStatus = getConn.getResponseCode();
            if (getStatus != 200) {
                log.warn("NorteCambiosScraper: GET home retorno status {}", getStatus);
                return Collections.emptyMap();
            }

            String html = readResponse(getConn);
            String sessionCookie = extractSessionCookie(getConn);
            getConn.disconnect();

            if (html == null || html.isEmpty()) {
                log.warn("NorteCambiosScraper: respuesta vacia del home");
                return Collections.emptyMap();
            }

            String csrf = extractCsrf(html);
            String snapshot = extractSnapshot(html);

            if (csrf == null || snapshot == null) {
                log.warn("NorteCambiosScraper: no se pudo extraer csrf={}, snapshot={}, cookie={}",
                        csrf != null, snapshot != null, sessionCookie != null);
                return Collections.emptyMap();
            }

            log.debug("NorteCambiosScraper: GET ok. csrf={}..., snapshot len={}, cookie={}",
                    csrf.substring(0, Math.min(10, csrf.length())), snapshot.length(), sessionCookie != null);

            // Paso 2: POST /livewire/update — setActiveBranch(8)
            HttpsURLConnection postConn = openConnection(LIVEWIRE_URL);
            postConn.setRequestMethod("POST");
            postConn.setDoOutput(true);
            postConn.setRequestProperty("Content-Type", "application/json");
            postConn.setRequestProperty("Accept", "application/json, text/plain, */*");
            postConn.setRequestProperty("Accept-Language", "es-PY,es;q=0.9");
            postConn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            postConn.setRequestProperty("X-Livewire", "");
            postConn.setRequestProperty("X-CSRF-TOKEN", csrf);
            postConn.setRequestProperty("Referer", BASE_URL);
            if (sessionCookie != null) {
                postConn.setRequestProperty("Cookie", sessionCookie);
            }

            String body = buildLivewireBody(snapshot);
            try (OutputStream os = postConn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int postStatus = postConn.getResponseCode();
            if (postStatus != 200) {
                log.warn("NorteCambiosScraper: POST livewire/update retorno status {}", postStatus);
                String errorBody = readErrorResponse(postConn);
                log.debug("NorteCambiosScraper: error response: {}", errorBody);
                postConn.disconnect();
                return Collections.emptyMap();
            }

            String responseBody = readResponse(postConn);
            postConn.disconnect();

            if (responseBody == null || responseBody.isEmpty()) {
                log.warn("NorteCambiosScraper: respuesta vacia del livewire/update");
                return Collections.emptyMap();
            }

            // Paso 3: Extraer data.text del snapshot en la respuesta
            String ratesText = extractRatesText(responseBody);
            if (ratesText == null || ratesText.isEmpty()) {
                log.warn("NorteCambiosScraper: no se encontro texto de cotizaciones en la respuesta");
                return Collections.emptyMap();
            }

            // Paso 4: Parsear tasas
            Map<String, double[]> rates = parseRatesText(ratesText);
            if (rates.isEmpty()) {
                log.warn("NorteCambiosScraper: no se pudieron parsear cotizaciones del texto");
            } else {
                log.info("NorteCambiosScraper: {} monedas obtenidas de nortecambios", rates.size());
            }
            return rates;

        } catch (Exception e) {
            log.warn("NorteCambiosScraper: error al obtener cotizaciones: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private HttpsURLConnection openConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        if (sslSocketFactory != null) {
            conn.setSSLSocketFactory(sslSocketFactory);
        }
        conn.setHostnameVerifier(hostnameVerifier);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private String readResponse(HttpsURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private String readErrorResponse(HttpsURLConnection conn) {
        try {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream == null) return "(no error stream)";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
            }
        } catch (Exception e) {
            return "(error reading error stream: " + e.getMessage() + ")";
        }
    }

    private String extractCsrf(String html) {
        Matcher m = CSRF_PATTERN.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private String extractSnapshot(String html) {
        Matcher m = SNAPSHOT_PATTERN.matcher(html);
        if (!m.find()) return null;
        // El atributo contiene HTML entities — decodificar &quot; -> "
        String encoded = m.group(1);
        return encoded.replace("&quot;", "\"")
                      .replace("&amp;", "&")
                      .replace("&lt;", "<")
                      .replace("&gt;", ">")
                      .replace("&#039;", "'");
    }

    private String extractSessionCookie(HttpsURLConnection conn) {
        // Recoger todas las cookies Set-Cookie
        List<String> cookies = new ArrayList<>();
        Map<String, List<String>> headers = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                cookies.addAll(entry.getValue());
            }
        }
        if (cookies.isEmpty()) return null;

        // Concatenar todas las cookies como name=value separadas por "; "
        StringBuilder sb = new StringBuilder();
        for (String cookie : cookies) {
            int semicolon = cookie.indexOf(';');
            String nameValue = semicolon > 0 ? cookie.substring(0, semicolon) : cookie;
            if (sb.length() > 0) sb.append("; ");
            sb.append(nameValue);
        }
        return sb.toString();
    }

    private String buildLivewireBody(String snapshot) {
        // Escapar el snapshot para embeber como string JSON dentro del body JSON
        String escapedSnapshot = snapshot.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"components\":[{\"snapshot\":\"" + escapedSnapshot + "\",\"updates\":{},\"calls\":[{\"path\":\"\",\"method\":\"setActiveBranch\",\"params\":[" + BRANCH_ID + "]}]}]}";
    }

    private String extractRatesText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode components = root.get("components");
            if (components == null || !components.isArray() || components.size() == 0) return null;

            JsonNode snapshotStr = components.get(0).get("snapshot");
            if (snapshotStr == null) return null;

            // snapshot es un JSON string dentro del JSON — parsearlo
            String snapshotJson = snapshotStr.isTextual() ? snapshotStr.asText() : snapshotStr.toString();
            JsonNode snapshotNode = objectMapper.readTree(snapshotJson);

            JsonNode dataNode = snapshotNode.get("data");
            if (dataNode == null) return null;

            JsonNode textNode = dataNode.get("text");
            if (textNode == null) return null;

            // text puede ser un array [value, metadata] en Livewire 3
            if (textNode.isArray() && textNode.size() > 0) {
                return textNode.get(0).asText();
            }
            return textNode.asText();

        } catch (Exception e) {
            log.warn("NorteCambiosScraper: error parseando respuesta JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parsea el texto de cotizaciones formateado con emojis.
     * Formato esperado por bloque:
     *   [emoji] Nombre Moneda
     *   Compra: X.XXX [emoji] Venta: X.XXX [emoji]
     *
     * Mapeo invertido:
     *   nortecambios "Compra" -> nuestro valorEnGsVentaMercado
     *   nortecambios "Venta"  -> nuestro valorEnGsCompraMercado
     */
    Map<String, double[]> parseRatesText(String text) {
        Map<String, double[]> result = new LinkedHashMap<>();

        // Separar por doble salto de linea en bloques
        String[] blocks = text.split("\n\n");

        for (String block : blocks) {
            String[] lines = block.trim().split("\n");
            if (lines.length < 2) continue;

            // Primera linea: nombre de moneda (con emoji flags)
            String nameLine = lines[0].trim();
            String monedaKey = mapMonedaKey(nameLine);
            if (monedaKey == null) continue;

            // Buscar linea con Compra/Venta
            for (int i = 1; i < lines.length; i++) {
                Matcher rateMatcher = RATE_PATTERN.matcher(lines[i]);
                if (rateMatcher.find()) {
                    try {
                        double compra = parseRate(rateMatcher.group(1));
                        double venta = parseRate(rateMatcher.group(2));

                        // Mapeo invertido: compra nortecambios -> ventaMercado nuestro
                        result.put(monedaKey, new double[]{compra, venta});
                        log.debug("NorteCambiosScraper: {} -> ventaMercado={}, compraMercado={}", monedaKey, compra, venta);
                    } catch (NumberFormatException e) {
                        log.warn("NorteCambiosScraper: error parseando numeros para {}: {}", monedaKey, e.getMessage());
                    }
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Mapea nombre de moneda del texto a la denominacion en nuestro sistema.
     * Solo mapea las 3 monedas que nos interesan, ignora arbitrajes (Real x Dolar, etc).
     */
    private String mapMonedaKey(String nameLine) {
        String lower = nameLine.toLowerCase();

        // Descartar arbitrajes (contienen " x " entre monedas)
        if (lower.contains(" x ")) return null;

        if (lower.contains("dólar") || lower.contains("dolar") || lower.contains("dollar")) return "DOLAR";
        if (lower.contains("real")) return "REAL";
        if (lower.contains("peso")) return "PESO ARG";

        // Euro y otras — ignorar por ahora
        return null;
    }

    /**
     * Parsea un string de tasa como "6.120" o "1.250" o "3,00" a double.
     * Convenciones:
     *   - Punto como separador de miles en valores >= 1000 (ej: "6.120" = 6120)
     *   - Coma como separador decimal en valores < 100 (ej: "3,00" = 3.0)
     *   - Punto como separador decimal en valores con parte decimal (ej: "1.250" podria ser 1250 o 1.25)
     *
     * Heuristica: si el numero tiene formato X.XXX (1 digito, punto, 3 digitos) es separador de miles.
     */
    private double parseRate(String raw) {
        String cleaned = raw.trim();

        // Caso con coma decimal (ej: "3,00", "5,03")
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(".", ""); // quitar miles si hubiera
            cleaned = cleaned.replace(",", "."); // coma -> punto decimal
            return Double.parseDouble(cleaned);
        }

        // Caso con punto — determinar si es miles o decimal
        if (cleaned.contains(".")) {
            String[] parts = cleaned.split("\\.");
            if (parts.length == 2 && parts[1].length() == 3) {
                // X.XXX -> separador de miles (ej: "6.120" = 6120, "1.205" = 1205)
                return Double.parseDouble(cleaned.replace(".", ""));
            } else {
                // Punto decimal normal
                return Double.parseDouble(cleaned);
            }
        }

        // Sin separador
        return Double.parseDouble(cleaned);
    }
}
