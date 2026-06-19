package com.franco.dev.service.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Llama a OpenAI Vision (gpt-4o por defecto) con una o varias imagenes JPEG/PNG y extrae
 * los datos de una factura.
 * Usa endpoint /v1/chat/completions con image_url base64 + JSON mode + temperature 0.
 * API key + modelo + prompt adicional leidos de ConfiguracionSistemaService.
 *
 * Una factura puede venir en varias paginas (PDF multipagina convertido, o varias fotos).
 * Todas las paginas se envian en UN solo request (varios bloques image_url) para que el
 * modelo consolide cabecera + items continuados en un unico JSON.
 */
@Service
public class OpenAiVisionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVisionService.class);

    private static final String PROMPT_BASE =
            "Eres un asistente especializado en facturas y notas comerciales de Paraguay para empresas " +
            "farmaceuticas y de distribucion de mercaderias. Analiza la imagen y extrae los datos en JSON " +
            "estricto. Pueden ser: (a) Documentos Electronicos SIFEN escaneados, (b) facturas legales en " +
            "papel con timbrado, o (c) notas comunes del proveedor (remito, nota de pedido, listado simple " +
            "sin timbrado oficial). Campos: emisorRuc (formato XXXXXXXX-X si esta; null si no), " +
            "emisorNombre, numeroFactura (o numero de nota si no es legal), timbrado (null si es nota " +
            "comun sin timbrado), fechaEmision (YYYY-MM-DD), moneda (PYG/USD), totalGeneral, esLegal " +
            "(boolean: true si tiene timbrado valido y formato de factura legal, false si es nota comun). " +
            "Items: codigoProducto, nombreProducto, cantidad, precioUnitario, descuento (0 si no hay), " +
            "totalItem. Si un campo no es legible: null. No inventes datos. Si recibes varias imagenes, " +
            "son paginas del MISMO documento en orden: consolida todos los items de todas las paginas en " +
            "una sola lista (los items pueden continuar de una pagina a la otra), y devuelve la cabecera y " +
            "el totalGeneral una sola vez (no los repitas por pagina). Si la imagen no es factura ni " +
            "nota comercial: {\"error\":\"NO_ES_DOCUMENTO_VALIDO\"}.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ConfiguracionSistemaService configService;

    @Value("${app.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${app.openai.timeout-ms:60000}")
    private int timeoutMs;

    /** Resultado de un analisis: payload deserializado + metadata de uso. */
    public static class Resultado {
        public final FacturaIaResponse data;
        public final String rawJson;
        public final Integer tokensPrompt;
        public final Integer tokensRespuesta;
        public final String modeloUsado;

        public Resultado(FacturaIaResponse data, String rawJson, Integer tokensPrompt,
                         Integer tokensRespuesta, String modeloUsado) {
            this.data = data;
            this.rawJson = rawJson;
            this.tokensPrompt = tokensPrompt;
            this.tokensRespuesta = tokensRespuesta;
            this.modeloUsado = modeloUsado;
        }
    }

    /** Una imagen (pagina) a analizar: bytes + su mimeType. */
    public static class ImagenParaAnalisis {
        public final byte[] bytes;
        public final String mimeType;

        public ImagenParaAnalisis(byte[] bytes, String mimeType) {
            this.bytes = bytes;
            this.mimeType = mimeType;
        }
    }

    /**
     * Overload de una sola imagen. Delega en {@link #analizarImagenes}.
     *
     * @param jpegBytes imagen JPEG (o PNG, en cuyo caso pasar mimeType image/png)
     * @param mimeType  ej. "image/jpeg" o "image/png"
     */
    public Resultado analizarImagen(byte[] jpegBytes, String mimeType) throws IOException {
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new IOException("Imagen vacia");
        }
        return analizarImagenes(Collections.singletonList(new ImagenParaAnalisis(jpegBytes, mimeType)));
    }

    /**
     * Analiza una o varias paginas de un mismo documento en UN solo request a Vision.
     *
     * @param imagenes paginas en orden (cada una con sus bytes y mimeType)
     * @return datos extraidos + raw response + uso de tokens
     * @throws IOException si la API responde error o la respuesta no es parseable
     */
    public Resultado analizarImagenes(List<ImagenParaAnalisis> imagenes) throws IOException {
        if (imagenes == null || imagenes.isEmpty()) {
            throw new IOException("Sin imagenes para analizar");
        }
        for (ImagenParaAnalisis im : imagenes) {
            if (im == null || im.bytes == null || im.bytes.length == 0) {
                throw new IOException("Imagen vacia");
            }
        }

        String apiKey = configService.getDecrypted("openai.api_key")
                .orElseThrow(() -> new IOException("API key OpenAI no configurada"));
        String modelo = configService.findByClave("openai.modelo")
                .map(c -> c.getValor()).orElse("gpt-4o");
        if (modelo == null || modelo.isEmpty()) modelo = "gpt-4o";

        String promptAdicional = configService.findByClave("openai.prompt_adicional")
                .map(c -> c.getValor()).orElse("");
        String promptFinal = PROMPT_BASE;
        if (promptAdicional != null && !promptAdicional.trim().isEmpty()) {
            promptFinal = promptFinal + "\n\n" + promptAdicional.trim();
        }

        List<String> dataUrls = new ArrayList<>(imagenes.size());
        for (ImagenParaAnalisis im : imagenes) {
            dataUrls.add("data:" + im.mimeType + ";base64,"
                    + Base64.getEncoder().encodeToString(im.bytes));
        }
        String requestBody = buildRequestBody(modelo, promptFinal, dataUrls);

        URL url = new URL(baseUrl + "/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String body = leerStream(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IOException("OpenAI HTTP " + code + ": " + truncar(body, 500));
            }

            return parsearRespuesta(body, modelo);
        } finally {
            conn.disconnect();
        }
    }

    String buildRequestBody(String modelo, String prompt, List<String> imagenDataUrls) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", modelo);
        root.put("temperature", 0);
        root.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));

        ArrayNode messages = MAPPER.createArrayNode();

        ObjectNode systemMsg = MAPPER.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", prompt);
        messages.add(systemMsg);

        ObjectNode userMsg = MAPPER.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode contentArray = MAPPER.createArrayNode();

        int n = imagenDataUrls.size();
        String texto = n == 1
                ? "Analiza esta imagen y extrae los datos en JSON segun el esquema indicado."
                : "Analiza estas " + n + " paginas (en orden) de un mismo documento y consolida los "
                        + "datos en UN solo JSON segun el esquema indicado.";
        ObjectNode textPart = MAPPER.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", texto);
        contentArray.add(textPart);

        for (String dataUrl : imagenDataUrls) {
            ObjectNode imagePart = MAPPER.createObjectNode();
            imagePart.put("type", "image_url");
            ObjectNode imgUrl = MAPPER.createObjectNode();
            imgUrl.put("url", dataUrl);
            imagePart.set("image_url", imgUrl);
            contentArray.add(imagePart);
        }

        userMsg.set("content", contentArray);
        messages.add(userMsg);

        root.set("messages", messages);
        return root.toString();
    }

    Resultado parsearRespuesta(String responseBody, String modeloSolicitado) throws IOException {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new IOException("Respuesta OpenAI sin choices: " + truncar(responseBody, 300));
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (content.isEmpty()) {
            throw new IOException("Respuesta OpenAI sin content");
        }

        FacturaIaResponse data = MAPPER.readValue(content, FacturaIaResponse.class);

        JsonNode usage = root.path("usage");
        Integer promptTok = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
        Integer compTok = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
        String modeloUsado = root.path("model").asText(modeloSolicitado);

        return new Resultado(data, content, promptTok, compTok, modeloUsado);
    }

    private static String leerStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
