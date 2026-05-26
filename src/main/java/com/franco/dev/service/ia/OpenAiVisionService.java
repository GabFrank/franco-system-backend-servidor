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
import java.util.Base64;
import java.util.Optional;

/**
 * Llama a OpenAI Vision (gpt-4o por defecto) con una imagen JPEG y extrae datos de factura.
 * Usa endpoint /v1/chat/completions con image_url base64 + JSON mode + temperature 0.
 * API key + modelo + prompt adicional leidos de ConfiguracionSistemaService.
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
            "totalItem. Si un campo no es legible: null. No inventes datos. Si la imagen no es factura ni " +
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

    /**
     * @param jpegBytes imagen JPEG (o PNG, en cuyo caso pasar mimeType image/png)
     * @param mimeType  ej. "image/jpeg" o "image/png"
     * @return datos extraidos + raw response + uso de tokens
     * @throws IOException si la API responde error o la respuesta no es parseable
     */
    public Resultado analizarImagen(byte[] jpegBytes, String mimeType) throws IOException {
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new IOException("Imagen vacia");
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

        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(jpegBytes);
        String requestBody = buildRequestBody(modelo, promptFinal, dataUrl);

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

    String buildRequestBody(String modelo, String prompt, String imagenDataUrl) {
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

        ObjectNode textPart = MAPPER.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", "Analiza esta imagen y extrae los datos en JSON segun el esquema indicado.");
        contentArray.add(textPart);

        ObjectNode imagePart = MAPPER.createObjectNode();
        imagePart.put("type", "image_url");
        ObjectNode imgUrl = MAPPER.createObjectNode();
        imgUrl.put("url", imagenDataUrl);
        imagePart.set("image_url", imgUrl);
        contentArray.add(imagePart);

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
