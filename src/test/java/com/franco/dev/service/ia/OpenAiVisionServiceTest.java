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
    OpenAiVisionService service;

    @BeforeEach
    void setUp() {
        lenient().when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_API_KEY)).thenReturn(Optional.of("sk-test"));
        lenient().when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_MODELO)).thenReturn(Optional.empty());
        lenient().when(config.valor(ConfiguracionSistemaService.CLAVE_OPENAI_PROMPT)).thenReturn(Optional.empty());
        service = new OpenAiVisionService(config) {
            @Override
            String post(String apiKey, String body) {
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
}
