package com.franco.dev.service.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiVisionServiceTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Mock
    ConfiguracionSistemaService config;

    final List<String> cuerpos = new ArrayList<>();
    final List<String> respuestas = new ArrayList<>();
    final List<String> clavesClaude = new ArrayList<>();
    OpenAiVisionService service;

    @BeforeEach
    void setUp() {
        lenient().when(config.valor(ConfiguracionSistemaService.CLAVE_IA_PROVEEDOR)).thenReturn(Optional.empty());
        lenient().when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_API_KEY)).thenReturn(Optional.of("sk-test"));
        lenient().when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_MODELO)).thenReturn(Optional.empty());
        lenient().when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_PROMPT)).thenReturn(Optional.empty());
        service = new OpenAiVisionService(config) {
            @Override
            String post(String apiKey, String body) {
                cuerpos.add(body);
                return respuestas.remove(0);
            }

            @Override
            String postClaude(String apiKey, String body) {
                clavesClaude.add(apiKey);
                cuerpos.add(body);
                return respuestas.remove(0);
            }
        };
    }

    static String respuesta(String content, String finish, int in, int out) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode root = M.createObjectNode();
        root.put("model", "gpt-4o-2024");
        com.fasterxml.jackson.databind.node.ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finish);
        choice.putObject("message").put("content", content);
        root.putObject("usage").put("prompt_tokens", in).put("completion_tokens", out);
        return M.writeValueAsString(root);
    }

    @Test
    void cuerpoPideJsonSchemaEstricto() throws Exception {
        respuestas.add(respuesta("{\"items\":[]}", "stop", 10, 5));
        service.leer(Collections.singletonList(new byte[]{0x41}));

        JsonNode b = M.readTree(cuerpos.get(0));
        assertEquals("gpt-4o", b.path("model").asText());
        assertEquals("json_schema", b.path("response_format").path("type").asText());
        assertTrue(b.path("response_format").path("json_schema").path("strict").asBoolean());
        assertEquals(0, b.path("temperature").asInt());
        assertEquals(8000, b.path("max_tokens").asInt());
        assertTrue(cuerpos.get(0).contains("FRANCO AREVALOS"));
        assertEquals(1, cuerpos.get(0).split("\"type\":\"image_url\"", -1).length - 1);
        assertTrue(cuerpos.get(0).contains("data:image/jpeg;base64,QQ=="));
        JsonNode schema = b.path("response_format").path("json_schema").path("schema");
        assertFalse(schema.path("additionalProperties").asBoolean(true));
        assertEquals("array", schema.path("properties").path("items").path("type").get(0).asText());
    }

    @Test
    void modeloYPromptAdicionalSeTomanDeLaConfiguracion() throws Exception {
        when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_MODELO)).thenReturn(Optional.of("gpt-4.1"));
        when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_PROMPT)).thenReturn(Optional.of("REGLA_EXTRA_X"));
        respuestas.add(respuesta("{\"items\":[]}", "stop", 1, 1));
        service.leer(Collections.singletonList(new byte[]{1}));
        assertTrue(cuerpos.get(0).contains("\"model\":\"gpt-4.1\""));
        assertTrue(cuerpos.get(0).contains("REGLA_EXTRA_X"));
    }

    @Test
    void montosConPuntoDeMilesSeNormalizan() throws Exception {
        String c = "{\"moneda\":\"PYG\",\"totalGeneral\":264.408,\"items\":["
                + "{\"precioUnitario\":11.017,\"totalItem\":264.408,\"cantidad\":24}]}";
        respuestas.add(respuesta(c, "stop", 1, 1));
        FacturaIaLeida f = service.leer(Collections.singletonList(new byte[]{1}));
        assertEquals(new BigDecimal("264408"), f.getTotalGeneral());
        assertEquals(new BigDecimal("11017"), f.getItems().get(0).getPrecioUnitario());
        assertEquals(new BigDecimal("264408"), f.getItems().get(0).getTotalItem());
        assertEquals(0, new BigDecimal("24").compareTo(f.getItems().get(0).getCantidad()));
    }

    @Test
    void normMontoPygYUsd() throws Exception {
        assertEquals(new BigDecimal("3263463"), OpenAiVisionService.normMonto(M.readTree("\"3.263.463\""), null));
        assertEquals(0, new BigDecimal("1234.50").compareTo(OpenAiVisionService.normMonto(M.readTree("\"1,234.50\""), "USD")));
    }

    @Test
    void respuestaCortadaDaErrorEntendible() throws Exception {
        respuestas.add(respuesta("{\"items\":[", "length", 1, 8000));
        IOException e = assertThrows(IOException.class, () -> service.leer(Collections.singletonList(new byte[]{1})));
        assertTrue(e.getMessage().contains("imagen más nítida"), e.getMessage());
    }

    @Test
    void paginasSeFusionan() throws Exception {
        respuestas.add(respuesta("{\"emisorRuc\":\"80012345-6\",\"emisorNombre\":\"PROV SA\",\"moneda\":\"PYG\",\"totalGeneral\":100,"
                + "\"dudosos\":[\"timbrado\"],\"items\":[{\"descripcion\":\"A\",\"totalItem\":100}]}", "stop", 10, 5));
        respuestas.add(respuesta("{\"totalGeneral\":300,\"dudosos\":[\"timbrado\",\"fechaEmision\"],"
                + "\"items\":[{\"descripcion\":\"B\",\"totalItem\":200}]}", "stop", 20, 7));
        FacturaIaLeida f = service.leer(Arrays.asList(new byte[]{1}, new byte[]{2}));
        assertEquals(2, cuerpos.size());
        assertEquals("80012345-6", f.getEmisorRuc());
        assertEquals(0, new BigDecimal("300").compareTo(f.getTotalGeneral()));
        assertEquals(2, f.getItems().size());
        assertEquals("B", f.getItems().get(1).getDescripcion());
        assertEquals(Integer.valueOf(30), f.getTokensEntrada());
        assertEquals(Integer.valueOf(12), f.getTokensSalida());
        assertEquals("gpt-4o-2024", f.getModelo());
        assertEquals(Arrays.asList("timbrado", "fechaEmision"), f.getDudosos());
        assertNull(f.getError());
        assertTrue(f.getRespuestaCruda().startsWith("["));
    }

    @Test
    void numeroCompletoSeParte() throws Exception {
        respuestas.add(respuesta("{\"numero\":\"001-002-0001234\",\"items\":[]}", "stop", 1, 1));
        FacturaIaLeida f = service.leer(Collections.singletonList(new byte[]{1}));
        assertEquals("001", f.getEstablecimiento());
        assertEquals("002", f.getPuntoExpedicion());
        assertEquals("0001234", f.getNumero());
    }

    @Test
    void sinClaveAvisaClaro() {
        when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_API_KEY)).thenReturn(Optional.empty());
        IOException e = assertThrows(IOException.class, () -> service.leer(Collections.singletonList(new byte[]{1})));
        assertTrue(e.getMessage().contains("no está configurada"));
    }

    @Test
    void sinMasterKeyAvisaClaro() {
        when(config.valor(anyString())).thenThrow(new GraphQLException("Falta CONFIG_MASTER_KEY"));
        IOException e = assertThrows(IOException.class, () -> service.leer(Collections.singletonList(new byte[]{1})));
        assertTrue(e.getMessage().contains("no está configurada"));
    }

    @Test
    void pygConDecimalRealSeRespeta() throws Exception {
        assertEquals(0, new BigDecimal("11017.5").compareTo(OpenAiVisionService.normMonto(M.readTree("11017.5"), "PYG")));
    }

    @Test
    void pygConTresDecimalesEsMiles() throws Exception {
        assertEquals(new BigDecimal("264408"), OpenAiVisionService.normMonto(M.readTree("264.408"), "PYG"));
    }

    @Test
    void numeroGrandeNoUsaNotacionCientifica() throws Exception {
        assertEquals(0, new BigDecimal("10000000.5").compareTo(OpenAiVisionService.normMonto(M.readTree("1.00000005E7"), "PYG")));
    }

    @Test
    void usdEnHojaSinMonedaRespetaDecimales() throws Exception {
        respuestas.add(respuesta("{\"moneda\":\"USD\",\"totalGeneral\":10.5,\"items\":[{\"descripcion\":\"A\",\"totalItem\":10.5}]}", "stop", 1, 1));
        respuestas.add(respuesta("{\"totalGeneral\":1234.5,\"items\":[{\"descripcion\":\"B\",\"totalItem\":1234.5}]}", "stop", 1, 1));
        FacturaIaLeida f = service.leer(Arrays.asList(new byte[]{1}, new byte[]{2}));
        assertEquals(0, new BigDecimal("1234.5").compareTo(f.getTotalGeneral()));
        assertEquals(0, new BigDecimal("1234.5").compareTo(f.getItems().get(1).getTotalItem()));
    }

    // ------------------------------------------------------------------ Claude

    void usarClaude(String modelo) {
        when(config.valor(ConfiguracionSistemaService.CLAVE_IA_PROVEEDOR)).thenReturn(Optional.of("CLAUDE"));
        when(config.valor(ConfiguracionSistemaService.CLAVE_ANTHROPIC_API_KEY)).thenReturn(Optional.of("sk-ant-test"));
        when(config.valor(ConfiguracionSistemaService.CLAVE_ANTHROPIC_MODELO)).thenReturn(Optional.ofNullable(modelo));
    }

    static String respuestaClaude(String json, String stop, int in, int out) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode root = M.createObjectNode();
        root.put("model", "claude-opus-5-5");
        root.put("stop_reason", stop);
        com.fasterxml.jackson.databind.node.ArrayNode content = root.putArray("content");
        content.addObject().put("type", "thinking").put("thinking", "");
        content.addObject().put("type", "text").put("text", json);
        root.putObject("usage").put("input_tokens", in).put("output_tokens", out);
        return M.writeValueAsString(root);
    }

    @Test
    void claudeCuerpoPideSalidaEstructuradaConLaImagen() throws Exception {
        usarClaude(null);
        respuestas.add(respuestaClaude("{\"items\":[]}", "end_turn", 10, 5));
        service.leer(Collections.singletonList(new byte[]{0x41}));

        assertEquals("sk-ant-test", clavesClaude.get(0));
        JsonNode b = M.readTree(cuerpos.get(0));
        assertEquals("claude-opus-5-5", b.path("model").asText());
        assertEquals(16000, b.path("max_tokens").asInt());
        assertFalse(b.has("temperature"));
        assertEquals("default", b.path("fallbacks").asText());
        assertTrue(b.path("system").asText().contains("FRANCO AREVALOS"));
        assertEquals("json_schema", b.path("output_config").path("format").path("type").asText());
        JsonNode img = b.path("messages").get(0).path("content").get(0);
        assertEquals("image", img.path("type").asText());
        assertEquals("image/jpeg", img.path("source").path("media_type").asText());
        assertEquals("QQ==", img.path("source").path("data").asText());
    }

    @Test
    void claudeEsquemaRespetaElLimiteDeCamposConNull() throws Exception {
        String schema = OpenAiVisionService.schemaClaude().toString();
        assertFalse(schema.contains("\"type\":["), schema);
        // Claude rechaza más de 16 campos con unión (anyOf / tipo array).
        int uniones = schema.split("\"anyOf\"", -1).length - 1;
        assertTrue(uniones <= 16, "uniones=" + uniones);

        JsonNode props = OpenAiVisionService.schemaClaude().path("properties");
        assertEquals("string", props.path("emisorRuc").path("type").asText());
        assertEquals("number", props.path("totalGeneral").path("anyOf").get(0).path("type").asText());
        assertEquals("null", props.path("totalGeneral").path("anyOf").get(1).path("type").asText());
        assertEquals("array", props.path("items").path("type").asText());
        JsonNode item = props.path("items").path("items");
        assertFalse(item.path("additionalProperties").asBoolean(true));
        assertEquals("string", item.path("properties").path("descripcion").path("type").asText());
        assertEquals("number", item.path("properties").path("cantidad").path("anyOf").get(0).path("type").asText());
    }

    @Test
    void claudeTextoVacioSeVuelveNull() throws Exception {
        usarClaude(null);
        respuestas.add(respuestaClaude("{\"emisorRuc\":\"\",\"timbrado\":\" \",\"emisorNombre\":\"ACME\","
                + "\"error\":\"\",\"dudosos\":[],\"items\":[{\"codigoBarras\":\"\",\"descripcion\":\"A\","
                + "\"totalItem\":null,\"dudosos\":[]}]}", "end_turn", 1, 1));
        FacturaIaLeida f = service.leer(Collections.singletonList(new byte[]{1}));
        assertNull(f.getEmisorRuc());
        assertNull(f.getTimbrado());
        assertNull(f.getError());
        assertEquals("ACME", f.getEmisorNombre());
        assertNull(f.getItems().get(0).getCodigoBarras());
        assertEquals("A", f.getItems().get(0).getDescripcion());
        assertTrue(M.readTree(cuerpos.get(0)).path("system").asText().contains("cadena vacía"));
    }

    @Test
    void claudeRespuestaPasaPorElMismoPostProceso() throws Exception {
        usarClaude("claude-haiku-5-5");
        respuestas.add(respuestaClaude("{\"numero\":\"001-002-0001234\",\"totalGeneral\":264.408,"
                + "\"items\":[{\"descripcion\":\"A\",\"totalItem\":264.408}]}", "end_turn", 100, 50));
        FacturaIaLeida f = service.leer(Collections.singletonList(new byte[]{1}));

        assertFalse(M.readTree(cuerpos.get(0)).has("fallbacks"));
        assertEquals("claude-opus-5-5", f.getModelo());
        assertEquals(100, f.getTokensEntrada());
        assertEquals(50, f.getTokensSalida());
        assertEquals("0001234", f.getNumero());
        assertEquals(new BigDecimal("264408"), f.getTotalGeneral());
        assertEquals(new BigDecimal("264408"), f.getItems().get(0).getTotalItem());
    }

    @Test
    void claudeCortadaAvisa() throws Exception {
        usarClaude(null);
        respuestas.add(respuestaClaude("{\"items\":[", "max_tokens", 1, 1));
        IOException e = assertThrows(IOException.class, () -> service.leer(Collections.singletonList(new byte[]{1})));
        assertTrue(e.getMessage().contains("cortada"));
    }

    @Test
    void claudeSinClaveAvisaClaro() {
        when(config.valor(ConfiguracionSistemaService.CLAVE_IA_PROVEEDOR)).thenReturn(Optional.of("CLAUDE"));
        when(config.valor(ConfiguracionSistemaService.CLAVE_ANTHROPIC_API_KEY)).thenReturn(Optional.empty());
        IOException e = assertThrows(IOException.class, () -> service.leer(Collections.singletonList(new byte[]{1})));
        assertTrue(e.getMessage().contains("no está configurada"));
    }
}
