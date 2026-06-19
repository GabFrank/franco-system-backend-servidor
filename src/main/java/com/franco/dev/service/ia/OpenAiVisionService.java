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
import java.math.BigDecimal;
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
            "sin timbrado oficial). EMISOR vs CLIENTE: el emisor es quien EMITE/VENDE la factura (el " +
            "PROVEEDOR, suele figurar en el encabezado/membrete arriba con su logo y timbrado). El CLIENTE/" +
            "comprador (campos 'Señor(es)', 'Razon Social', 'RUC/CI' del cuerpo) NO es el emisor: en estas " +
            "compras el cliente casi siempre es 'FRANCO AREVALOS S.A.' u otra razon social de Franco — " +
            "IGNORALO para emisorRuc/emisorNombre y usa SIEMPRE los datos del proveedor del encabezado. " +
            "Campos: emisorRuc (RUC del PROVEEDOR, formato XXXXXXXX-X si esta; null si no), " +
            "emisorNombre (nombre del PROVEEDOR), numeroFactura (o numero de nota si no es legal), timbrado (null si es nota " +
            "comun sin timbrado), fechaEmision (YYYY-MM-DD), moneda (PYG/USD), totalGeneral, esLegal " +
            "(boolean: true si tiene timbrado valido y formato de factura legal, false si es nota comun). " +
            "Items: codigoProducto, nombreProducto, cantidad, precioUnitario, descuento (0 si no hay), " +
            "totalItem. NUMEROS: en Paraguay el papel usa el PUNTO como separador de miles y la COMA como " +
            "decimal (ej '264.408' = doscientos sesenta y cuatro mil cuatrocientos ocho; '11.017,50' = once " +
            "mil diecisiete con 50). En el JSON devuelve los numeros CRUDOS sin separador de miles y usando " +
            "PUNTO como decimal: 264408, 11017, 11017.5. NUNCA pongas separador de miles en el JSON. " +
            "CANTIDADES: la columna de cantidad suele estar MANUSCRITA y es poco legible; si no estas seguro " +
            "de la cantidad, deducela como totalItem/precioUnitario (redondeando a entero) en vez de adivinar " +
            "el numero escrito a mano. precioUnitario y totalItem casi siempre estan impresos: priorizalos. " +
            "Si un campo no es legible: null. No inventes datos. Si recibes varias imagenes, " +
            "son paginas del MISMO documento en orden: consolida todos los items de todas las paginas en " +
            "una sola lista (los items pueden continuar de una pagina a la otra), y devuelve la cabecera y " +
            "el totalGeneral una sola vez (no los repitas por pagina). Si la imagen no es factura ni " +
            "nota comercial: {\"error\":\"NO_ES_DOCUMENTO_VALIDO\"}.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Tope de tokens de salida. Acota el costo y, sobre todo, evita que una imagen densa o de baja
     * calidad meta al modelo en un loop de repeticion de items sin fin. Si se alcanza, la respuesta
     * llega con finish_reason="length" (truncada) y la detectamos para dar un error claro.
     */
    private static final int MAX_TOKENS = 8000;

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
        root.put("max_tokens", MAX_TOKENS);
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
        String finishReason = choices.get(0).path("finish_reason").asText("");
        if ("length".equals(finishReason)) {
            // El modelo agoto el cupo de salida (tipico de imagen densa/baja calidad que lo hace
            // repetir items). La respuesta esta truncada: cortamos con un error entendible en vez
            // de fallar con un JsonEOF cripto.
            throw new IOException("La IA devolvio una respuesta truncada (demasiados items o imagen "
                    + "de baja calidad/poco legible). Probá con una imagen mas nitida o subí menos "
                    + "paginas por vez.");
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (content.isEmpty()) {
            throw new IOException("Respuesta OpenAI sin content");
        }

        FacturaIaResponse data = MAPPER.readValue(content, FacturaIaResponse.class);
        // El modelo a veces emite montos con el separador de miles paraguayo (264.408 -> 264.408
        // decimal en JSON). Normalizamos en codigo a partir del texto crudo, sin depender del prompt.
        normalizarMontos(data, MAPPER.readTree(content));

        JsonNode usage = root.path("usage");
        Integer promptTok = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
        Integer compTok = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
        String modeloUsado = root.path("model").asText(modeloSolicitado);

        return new Resultado(data, content, promptTok, compTok, modeloUsado);
    }

    /**
     * Reescribe los montos del FacturaIaResponse a partir del texto crudo de OpenAI, para evitar
     * que el separador de miles paraguayo se interprete como decimal (264.408 -> 264408).
     * No toca cantidad ni descuento (pueden ser fracciones o porcentajes legitimos).
     */
    static void normalizarMontos(FacturaIaResponse data, JsonNode contentNode) {
        if (data == null || contentNode == null) return;
        String moneda = data.getMoneda();

        JsonNode totalNode = contentNode.get("totalGeneral");
        if (totalNode != null && !totalNode.isNull()) {
            data.setTotalGeneral(normMonto(totalNode, moneda));
        }

        JsonNode itemsNode = contentNode.get("items");
        if (data.getItems() != null && itemsNode != null && itemsNode.isArray()) {
            int n = Math.min(data.getItems().size(), itemsNode.size());
            for (int i = 0; i < n; i++) {
                JsonNode itNode = itemsNode.get(i);
                FacturaIaResponse.Item it = data.getItems().get(i);
                if (itNode.get("precioUnitario") != null && !itNode.get("precioUnitario").isNull()) {
                    it.setPrecioUnitario(normMonto(itNode.get("precioUnitario"), moneda));
                }
                if (itNode.get("totalItem") != null && !itNode.get("totalItem").isNull()) {
                    it.setTotalItem(normMonto(itNode.get("totalItem"), moneda));
                }
            }
        }
    }

    /**
     * Convierte un monto del texto crudo a BigDecimal aplicando el formato paraguayo:
     * PYG (Guaranies, sin decimales) -> se quitan TODOS los . y , (son separadores de miles).
     * Otra moneda (USD) -> , es separador de miles y . es decimal.
     */
    static BigDecimal normMonto(JsonNode node, String moneda) {
        if (node == null || node.isNull()) return null;
        String s = node.asText().trim();
        if (s.isEmpty()) return null;
        boolean pyg = moneda == null || moneda.isEmpty()
                || moneda.equalsIgnoreCase("PYG") || moneda.equalsIgnoreCase("GS")
                || moneda.equalsIgnoreCase("Guarani") || moneda.equalsIgnoreCase("Guaranies");
        String limpio = pyg ? s.replace(".", "").replace(",", "")
                            : s.replace(",", "");
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            log.warn("Monto no parseable '{}' (moneda={}), se deja como vino", s, moneda);
            return node.isNumber() ? node.decimalValue() : null;
        }
    }

    /**
     * Fusiona los resultados de analizar cada pagina por separado en un unico FacturaIaResponse.
     * Estrategia multipagina (mas robusta que mandar todas las imagenes juntas, que hace al modelo
     * repetir items en loop): cabecera = primer valor no nulo (suele estar en pag 1); totalGeneral =
     * ultimo valor no nulo (el total general aparece en la ultima pagina); items = concatenacion.
     * Las paginas de continuacion que el modelo marca como "no documento valido" se ignoran salvo
     * que traigan items. Suma tokens de todas las llamadas.
     */
    public static Resultado mergeResultados(List<Resultado> partes) {
        if (partes == null || partes.isEmpty()) {
            throw new IllegalArgumentException("Sin resultados para fusionar");
        }
        if (partes.size() == 1) {
            return partes.get(0);
        }
        FacturaIaResponse merged = new FacturaIaResponse();
        List<FacturaIaResponse.Item> items = new ArrayList<>();
        int tokP = 0, tokR = 0;
        String modelo = null;
        List<String> raws = new ArrayList<>();
        boolean algunaValida = false;

        for (Resultado r : partes) {
            if (r == null) continue;
            if (r.rawJson != null) raws.add(r.rawJson);
            if (r.tokensPrompt != null) tokP += r.tokensPrompt;
            if (r.tokensRespuesta != null) tokR += r.tokensRespuesta;
            if (modelo == null) modelo = r.modeloUsado;

            FacturaIaResponse d = r.data;
            if (d == null) continue;
            boolean esError = d.getError() != null && !d.getError().isEmpty();
            if (!esError) {
                algunaValida = true;
                if (merged.getEmisorRuc() == null) merged.setEmisorRuc(d.getEmisorRuc());
                if (merged.getEmisorNombre() == null) merged.setEmisorNombre(d.getEmisorNombre());
                if (merged.getNumeroFactura() == null) merged.setNumeroFactura(d.getNumeroFactura());
                if (merged.getTimbrado() == null) merged.setTimbrado(d.getTimbrado());
                if (merged.getFechaEmision() == null) merged.setFechaEmision(d.getFechaEmision());
                if (merged.getMoneda() == null) merged.setMoneda(d.getMoneda());
                if (merged.getEsLegal() == null) merged.setEsLegal(d.getEsLegal());
                // total general: el ultimo no-nulo gana (suele estar en la ultima pagina)
                if (d.getTotalGeneral() != null) merged.setTotalGeneral(d.getTotalGeneral());
            }
            if (d.getItems() != null && !d.getItems().isEmpty()) {
                items.addAll(d.getItems());
                algunaValida = true;
            }
        }
        merged.setItems(items);
        if (!algunaValida) {
            merged.setError("NO_ES_DOCUMENTO_VALIDO");
        }
        String rawMerged = "[" + String.join(",", raws) + "]";
        return new Resultado(merged, rawMerged, tokP, tokR, modelo);
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
