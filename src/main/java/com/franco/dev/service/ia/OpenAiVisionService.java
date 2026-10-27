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
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee una factura en papel con IA: una llamada por página y fusión de los resultados.
 * El proveedor (OpenAI o Claude), la clave y el modelo salen de ConfiguracionSistemaService.
 * Los dos proveedores devuelven el mismo JSON (mismo esquema estricto) y pasan por el mismo post-proceso.
 */
@Service
public class OpenAiVisionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVisionService.class);

    private static final String SIN_CLAVE = "La lectura de facturas en papel no está configurada: "
            + "pedile a un administrador que cargue la clave de la IA.";

    /** Tope de tokens de salida para Claude: incluye su razonamiento interno, por eso es mayor que el de OpenAI. */
    private static final int MAX_TOKENS_CLAUDE = 16000;
    /** Modelos de Claude que aceptan el reintento automático en otro modelo si el primero rechaza la imagen. */
    private static final String[] MODELOS_CLAUDE_CON_FALLBACK =
            {"claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5"};

    private static final String PROMPT_BASE =
            "Sos un asistente especializado en facturas y notas comerciales de Paraguay para empresas " +
            "farmacéuticas y de distribución de mercaderías. Analizá la imagen y extraé los datos. Pueden ser " +
            "facturas legales en papel con timbrado o notas comunes del proveedor (remito, nota de pedido). " +
            "EMISOR vs CLIENTE: el emisor es quien EMITE/VENDE la factura (el PROVEEDOR; suele figurar en el " +
            "encabezado/membrete arriba con su logo y timbrado). El CLIENTE/comprador (campos 'Señor(es)', " +
            "'Razón Social', 'RUC/CI' del cuerpo) NO es el emisor: en estas compras el cliente casi siempre es " +
            "'FRANCO AREVALOS S.A.' u otra razón social de Franco. Poné al cliente en receptorRuc/receptorNombre " +
            "y usá SIEMPRE los datos del proveedor del encabezado para emisorRuc/emisorNombre (RUC con formato " +
            "XXXXXXXX-X si está). NÚMERO DE FACTURA: devolvelo partido en tres: establecimiento (3 dígitos), " +
            "puntoExpedicion (3 dígitos) y numero (hasta 7 dígitos), p. ej. 001-002-0001234. timbrado: número " +
            "de timbrado (null si es nota común). fechaEmision en formato YYYY-MM-DD. condicion: 'CONTADO' o " +
            "'CREDITO' (null si no se ve). moneda: PYG o USD. Ítems: codigoProveedor, codigoBarras, descripcion, " +
            "unidadMedida, cantidad, precioUnitario, descuento (0 si no hay), totalItem y tasaIva (0, 5 o 10, " +
            "según la columna de IVA donde figura el monto). NÚMEROS: en Paraguay el papel usa el PUNTO como " +
            "separador de miles y la COMA como decimal (ej '264.408' = doscientos sesenta y cuatro mil " +
            "cuatrocientos ocho; '11.017,50' = once mil diecisiete con 50). En el JSON devolvé los números " +
            "CRUDOS sin separador de miles y con PUNTO como decimal: 264408, 11017, 11017.5. CANTIDADES: la " +
            "columna de cantidad suele estar MANUSCRITA y es poco legible; si no estás seguro, deducila como " +
            "totalItem/precioUnitario (redondeando a entero) en vez de adivinar el número escrito a mano. " +
            "precioUnitario y totalItem casi siempre están impresos: priorizalos. Si un campo no es legible: " +
            "null; no inventes datos. En dudosos (de la factura y de cada ítem) listá los nombres de campo que " +
            "no pudiste leer con seguridad. Si la imagen no es factura ni nota comercial, devolvé el campo " +
            "error con 'NO_ES_DOCUMENTO_VALIDO'. Si recibís una página que continúa una factura, devolvé solo " +
            "lo que ves en ella.";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Lee los decimales como BigDecimal para no perder ceros finales (264.400). */
    private static final com.fasterxml.jackson.databind.ObjectReader LECTOR_EXACTO =
            MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Pattern NUMERO_COMPLETO = Pattern.compile("^\\s*(\\d{3})\\s*-\\s*(\\d{3})\\s*-\\s*(\\d+)\\s*$");

    /** Tope de tokens de salida: evita loops de repetición; si se alcanza llega finish_reason=length. */
    private static final int MAX_TOKENS = 8000;

    private final ConfiguracionSistemaService configService;

    @Value("${app.openai.base-url:https://api.openai.com}")
    private String baseUrl = "https://api.openai.com";

    @Value("${app.openai.timeout-ms:90000}")
    private int timeoutMs = 90000;

    @Value("${app.anthropic.base-url:https://api.anthropic.com}")
    private String anthropicBaseUrl = "https://api.anthropic.com";

    @Value("${app.anthropic.timeout-ms:180000}")
    private int anthropicTimeoutMs = 180000;

    @Autowired
    public OpenAiVisionService(ConfiguracionSistemaService configService) {
        this.configService = configService;
    }

    /** Lee las páginas (JPEG) de una factura; una llamada por página y fusión. */
    public FacturaIaLeida leer(List<byte[]> jpegs) throws IOException {
        if (jpegs == null || jpegs.isEmpty()) throw new IOException("Sin imágenes para analizar");
        for (byte[] j : jpegs) {
            if (j == null || j.length == 0) throw new IOException("Imagen vacía");
        }

        boolean claude;
        String apiKey;
        String modelo;
        String promptAdicional;
        try {
            claude = ConfiguracionSistemaService.PROVEEDOR_CLAUDE.equals(ConfiguracionSistemaService.normalizarProveedor(
                    configService.valor(ConfiguracionSistemaService.CLAVE_IA_PROVEEDOR).orElse(null)));
            if (claude) {
                apiKey = configService.valor(ConfiguracionSistemaService.CLAVE_ANTHROPIC_API_KEY).orElse(null);
                modelo = configService.valor(ConfiguracionSistemaService.CLAVE_ANTHROPIC_MODELO)
                        .filter(s -> !s.trim().isEmpty()).orElse(ConfiguracionSistemaService.MODELO_CLAUDE_POR_DEFECTO);
            } else {
                apiKey = configService.valor(ConfiguracionSistemaService.CLAVE_OPENAI_API_KEY).orElse(null);
                modelo = configService.valor(ConfiguracionSistemaService.CLAVE_OPENAI_MODELO)
                        .filter(s -> !s.trim().isEmpty()).orElse(ConfiguracionSistemaService.MODELO_POR_DEFECTO);
            }
            promptAdicional = configService.valor(ConfiguracionSistemaService.CLAVE_OPENAI_PROMPT).orElse("");
        } catch (RuntimeException e) {
            // Sin CONFIG_MASTER_KEY u otro fallo al leer la configuración: no exponer detalles.
            throw new IOException(SIN_CLAVE);
        }
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IOException(SIN_CLAVE);

        String prompt = PROMPT_BASE;
        if (promptAdicional != null && !promptAdicional.trim().isEmpty()) {
            prompt = prompt + "\n\n" + promptAdicional.trim();
        }

        List<FacturaIaLeida> partes = new ArrayList<>();
        for (byte[] jpeg : jpegs) {
            String base64 = Base64.getEncoder().encodeToString(jpeg);
            if (claude) {
                String respuesta = postClaude(apiKey.trim(), buildRequestBodyClaude(modelo, prompt, base64));
                partes.add(parsearRespuestaClaude(respuesta, modelo));
            } else {
                String respuesta = post(apiKey.trim(), buildRequestBody(modelo, prompt, "data:image/jpeg;base64," + base64));
                partes.add(parsearRespuesta(respuesta, modelo));
            }
        }
        // Moneda del documento fusionado (primer valor no nulo; null = PYG), igual para todas las hojas.
        String moneda = null;
        for (FacturaIaLeida d : partes) {
            if (moneda == null && d.getMoneda() != null && !d.getMoneda().isEmpty()) moneda = d.getMoneda();
        }
        for (FacturaIaLeida d : partes) {
            if (d.getRespuestaCruda() != null) normalizarMontos(d, LECTOR_EXACTO.readTree(d.getRespuestaCruda()), moneda);
        }
        return mergeResultados(partes);
    }

    /** POST a /v1/chat/completions; devuelve el cuerpo de la respuesta. Se sobrescribe en tests. */
    String post(String apiKey, String requestBody) throws IOException {
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
            return body;
        } finally {
            conn.disconnect();
        }
    }

    String buildRequestBody(String modelo, String prompt, String imagenDataUrl) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", modelo);
        root.put("temperature", 0);
        root.put("max_tokens", MAX_TOKENS);
        ObjectNode rf = root.putObject("response_format");
        rf.put("type", "json_schema");
        ObjectNode js = rf.putObject("json_schema");
        js.put("name", "factura");
        js.put("strict", true);
        js.set("schema", schema());

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text")
                .put("text", "Analizá esta imagen y extraé los datos según el esquema indicado.");
        ObjectNode img = content.addObject();
        img.put("type", "image_url");
        img.putObject("image_url").put("url", imagenDataUrl);
        return root.toString();
    }

    private static ObjectNode tipo(String t) {
        ObjectNode n = MAPPER.createObjectNode();
        n.putArray("type").add(t).add("null");
        return n;
    }

    private static ObjectNode objeto(String[] campos, String[] tipos) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("type", "object");
        ObjectNode props = o.putObject("properties");
        ArrayNode req = o.putArray("required");
        for (int i = 0; i < campos.length; i++) {
            ObjectNode p;
            if ("dudosos".equals(campos[i])) {
                p = tipo("array");
                p.putObject("items").put("type", "string");
            } else {
                p = tipo(tipos[i]);
            }
            props.set(campos[i], p);
            req.add(campos[i]);
        }
        o.put("additionalProperties", false);
        return o;
    }

    static ObjectNode schema() {
        ObjectNode item = objeto(
                new String[]{"codigoProveedor", "codigoBarras", "descripcion", "unidadMedida", "cantidad",
                        "precioUnitario", "descuento", "totalItem", "tasaIva", "dudosos"},
                new String[]{"string", "string", "string", "string", "number",
                        "number", "number", "number", "integer", "array"});
        ObjectNode root = objeto(
                new String[]{"emisorRuc", "emisorNombre", "receptorRuc", "receptorNombre", "timbrado",
                        "establecimiento", "puntoExpedicion", "numero", "fechaEmision", "condicion", "moneda",
                        "totalGeneral", "dudosos", "error", "items"},
                new String[]{"string", "string", "string", "string", "string",
                        "string", "string", "string", "string", "string", "string",
                        "number", "array", "string", "array"});
        ObjectNode items = (ObjectNode) root.path("properties").path("items");
        items.set("items", item);
        return root;
    }

    FacturaIaLeida parsearRespuesta(String responseBody, String modeloSolicitado) throws IOException {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new IOException("Respuesta de OpenAI sin resultados: " + truncar(responseBody, 300));
        }
        if ("length".equals(choices.get(0).path("finish_reason").asText(""))) {
            throw new IOException("La IA devolvió una respuesta cortada (demasiados ítems o foto poco legible). "
                    + "Probá con una imagen más nítida o subí menos páginas por vez.");
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (content.isEmpty()) throw new IOException("Respuesta de OpenAI sin contenido");

        JsonNode usage = root.path("usage");
        return parsearContenido(content,
                root.path("model").asText(modeloSolicitado),
                usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
    }

    /** Convierte el JSON que devolvió la IA (cualquier proveedor) en FacturaIaLeida. */
    FacturaIaLeida parsearContenido(String content, String modelo, Integer tokensEntrada, Integer tokensSalida)
            throws IOException {
        JsonNode node = MAPPER.readTree(content);
        FacturaIaLeida data = MAPPER.treeToValue(node, FacturaIaLeida.class);
        if (data.getDudosos() == null) data.setDudosos(new ArrayList<>());
        if (data.getItems() == null) data.setItems(new ArrayList<>());
        for (FacturaIaLeida.Item it : data.getItems()) {
            if (it.getDudosos() == null) it.setDudosos(new ArrayList<>());
        }
        // Los montos se normalizan en leer(), una sola vez, con la moneda del documento fusionado.
        partirNumero(data);

        data.setTokensEntrada(tokensEntrada);
        data.setTokensSalida(tokensSalida);
        data.setModelo(modelo);
        data.setRespuestaCruda(content);
        return data;
    }

    // ------------------------------------------------------------------ Claude

    /** POST a la Messages API de Claude; devuelve el cuerpo de la respuesta. Se sobrescribe en tests. */
    String postClaude(String apiKey, String requestBody) throws IOException {
        URL url = new URL(anthropicBaseUrl.replaceAll("/+$", "") + "/v1/messages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", ConfiguracionSistemaService.ANTHROPIC_VERSION);
            if (MAPPER.readTree(requestBody).has("fallbacks")) {
                conn.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01");
            }
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(anthropicTimeoutMs);
            conn.setReadTimeout(anthropicTimeoutMs);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String body = leerStream(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IOException("Claude HTTP " + code + ": " + truncar(body.replace(apiKey, "***"), 500));
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Pedido a Claude: la imagen + el mismo prompt y esquema que OpenAI, con salida JSON estructurada
     * (output_config.format). Sin temperature: la versión 2026-10-21 de la API la rechaza.
     */
    String buildRequestBodyClaude(String modelo, String prompt, String imagenBase64) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", modelo);
        root.put("max_tokens", MAX_TOKENS_CLAUDE);
        root.put("system", prompt);
        ObjectNode format = root.putObject("output_config").putObject("format");
        format.put("type", "json_schema");
        format.set("schema", schemaClaude());
        if (admiteFallback(modelo)) root.put("fallbacks", "default");

        ArrayNode messages = root.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        ObjectNode img = content.addObject();
        img.put("type", "image");
        ObjectNode source = img.putObject("source");
        source.put("type", "base64");
        source.put("media_type", "image/jpeg");
        source.put("data", imagenBase64);
        content.addObject().put("type", "text")
                .put("text", "Analizá esta imagen y extraé los datos según el esquema indicado.");
        return root.toString();
    }

    static boolean admiteFallback(String modelo) {
        for (String m : MODELOS_CLAUDE_CON_FALLBACK) if (m.equals(modelo)) return true;
        return false;
    }

    /** El mismo esquema que OpenAI, con los tipos "X o null" escritos como anyOf. */
    static ObjectNode schemaClaude() {
        return (ObjectNode) anyOfEnVezDeTipoNull(schema());
    }

    private static JsonNode anyOfEnVezDeTipoNull(JsonNode n) {
        if (!n.isObject()) return n;
        ObjectNode o = ((ObjectNode) n).deepCopy();
        JsonNode props = o.get("properties");
        if (props != null && props.isObject()) {
            ObjectNode nuevas = MAPPER.createObjectNode();
            props.fields().forEachRemaining(e -> nuevas.set(e.getKey(), anyOfEnVezDeTipoNull(e.getValue())));
            o.set("properties", nuevas);
        }
        if (o.has("items")) o.set("items", anyOfEnVezDeTipoNull(o.get("items")));
        JsonNode tipo = o.get("type");
        if (tipo != null && tipo.isArray() && tipo.size() == 2 && "null".equals(tipo.get(1).asText())) {
            o.put("type", tipo.get(0).asText());
            ObjectNode alternativa = MAPPER.createObjectNode();
            ArrayNode anyOf = alternativa.putArray("anyOf");
            anyOf.add(o);
            anyOf.addObject().put("type", "null");
            return alternativa;
        }
        return o;
    }

    FacturaIaLeida parsearRespuestaClaude(String responseBody, String modeloSolicitado) throws IOException {
        JsonNode root = MAPPER.readTree(responseBody);
        String stop = root.path("stop_reason").asText("");
        if ("max_tokens".equals(stop)) {
            throw new IOException("La IA devolvió una respuesta cortada (demasiados ítems o foto poco legible). "
                    + "Probá con una imagen más nítida o subí menos páginas por vez.");
        }
        if ("refusal".equals(stop)) {
            throw new IOException("Claude no quiso procesar la imagen. Probá con otra foto o con OpenAI.");
        }
        StringBuilder texto = new StringBuilder();
        for (JsonNode b : root.path("content")) {
            if ("text".equals(b.path("type").asText())) texto.append(b.path("text").asText(""));
        }
        if (texto.length() == 0) throw new IOException("Respuesta de Claude sin contenido: " + truncar(responseBody, 300));
        JsonNode usage = root.path("usage");
        return parsearContenido(texto.toString(),
                root.path("model").asText(modeloSolicitado),
                usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null,
                usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null);
    }

    /** Si el modelo devolvió el número completo (001-002-0001234), lo parte en tres. */
    static void partirNumero(FacturaIaLeida d) {
        if (d.getNumero() == null) return;
        Matcher m = NUMERO_COMPLETO.matcher(d.getNumero());
        if (m.matches()) {
            d.setEstablecimiento(m.group(1));
            d.setPuntoExpedicion(m.group(2));
            d.setNumero(m.group(3));
        }
    }

    /**
     * Reescribe los montos a partir del texto crudo para que el punto de miles paraguayo
     * no se lea como decimal (264.408 -> 264408). No toca cantidad ni descuento.
     */
    static void normalizarMontos(FacturaIaLeida data, JsonNode contentNode, String moneda) {
        if (data == null || contentNode == null) return;
        JsonNode total = contentNode.get("totalGeneral");
        if (total != null && !total.isNull()) data.setTotalGeneral(normMonto(total, moneda));

        JsonNode itemsNode = contentNode.get("items");
        if (data.getItems() != null && itemsNode != null && itemsNode.isArray()) {
            int n = Math.min(data.getItems().size(), itemsNode.size());
            for (int i = 0; i < n; i++) {
                JsonNode itNode = itemsNode.get(i);
                FacturaIaLeida.Item it = data.getItems().get(i);
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
     * Número JSON: PYG con exactamente 3 decimales = punto de miles (264.408 -> 264408); con 1 o 2
     * se respeta el decimal. Texto PYG: se quitan . y ,. USD: número tal cual; texto sin comas de miles.
     */
    static BigDecimal normMonto(JsonNode node, String moneda) {
        if (node == null || node.isNull()) return null;
        boolean pyg = moneda == null || moneda.isEmpty()
                || moneda.equalsIgnoreCase("PYG") || moneda.equalsIgnoreCase("GS")
                || moneda.equalsIgnoreCase("Guarani") || moneda.equalsIgnoreCase("Guaranies");
        if (node.isNumber()) {
            BigDecimal v = node.decimalValue();
            if (pyg && v.scale() == 3) return v.movePointRight(3).setScale(0);
            return v;
        }
        String s = node.asText().trim();
        if (s.isEmpty()) return null;
        String limpio = pyg ? s.replace(".", "").replace(",", "") : s.replace(",", "");
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            log.warn("Monto no parseable '{}' (moneda={}), se deja como vino", s, moneda);
            return null;
        }
    }

    /**
     * Fusiona las páginas: cabecera = primer valor no nulo, totalGeneral = último no nulo,
     * ítems concatenados, dudosos sin repetir, tokens sumados. Una página sin ítems marcada
     * como error se ignora mientras otra sea válida.
     */
    static FacturaIaLeida mergeResultados(List<FacturaIaLeida> partes) {
        if (partes == null || partes.isEmpty()) throw new IllegalArgumentException("Sin resultados para fusionar");
        if (partes.size() == 1) return partes.get(0);

        FacturaIaLeida m = new FacturaIaLeida();
        List<String> raws = new ArrayList<>();
        int tokIn = 0, tokOut = 0;
        boolean algunaValida = false;

        for (FacturaIaLeida d : partes) {
            if (d == null) continue;
            if (d.getRespuestaCruda() != null) raws.add(d.getRespuestaCruda());
            if (d.getTokensEntrada() != null) tokIn += d.getTokensEntrada();
            if (d.getTokensSalida() != null) tokOut += d.getTokensSalida();
            if (m.getModelo() == null) m.setModelo(d.getModelo());

            boolean esError = d.getError() != null && !d.getError().isEmpty();
            if (!esError) {
                algunaValida = true;
                if (m.getEmisorRuc() == null) m.setEmisorRuc(d.getEmisorRuc());
                if (m.getEmisorNombre() == null) m.setEmisorNombre(d.getEmisorNombre());
                if (m.getReceptorRuc() == null) m.setReceptorRuc(d.getReceptorRuc());
                if (m.getReceptorNombre() == null) m.setReceptorNombre(d.getReceptorNombre());
                if (m.getTimbrado() == null) m.setTimbrado(d.getTimbrado());
                if (m.getEstablecimiento() == null) m.setEstablecimiento(d.getEstablecimiento());
                if (m.getPuntoExpedicion() == null) m.setPuntoExpedicion(d.getPuntoExpedicion());
                if (m.getNumero() == null) m.setNumero(d.getNumero());
                if (m.getFechaEmision() == null) m.setFechaEmision(d.getFechaEmision());
                if (m.getCondicion() == null) m.setCondicion(d.getCondicion());
                if (m.getMoneda() == null) m.setMoneda(d.getMoneda());
                if (d.getTotalGeneral() != null) m.setTotalGeneral(d.getTotalGeneral());
            }
            if (d.getDudosos() != null) {
                for (String s : d.getDudosos()) if (!m.getDudosos().contains(s)) m.getDudosos().add(s);
            }
            if (d.getItems() != null && !d.getItems().isEmpty()) {
                m.getItems().addAll(d.getItems());
                algunaValida = true;
            }
        }
        m.setTokensEntrada(tokIn);
        m.setTokensSalida(tokOut);
        m.setRespuestaCruda("[" + String.join(",", raws) + "]");
        if (!algunaValida) m.setError("NO_ES_DOCUMENTO_VALIDO");
        return m;
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
