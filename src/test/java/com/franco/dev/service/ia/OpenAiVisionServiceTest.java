package com.franco.dev.service.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiVisionServiceTest {

    @Mock
    private ConfiguracionSistemaService configService;

    private OpenAiVisionService service;
    private HttpServer mockServer;
    private int mockPort;
    private AtomicReference<String> capturedAuth = new AtomicReference<>();
    private AtomicReference<String> capturedBody = new AtomicReference<>();
    private AtomicReference<String> mockResponse = new AtomicReference<>(defaultOpenAiResponse());
    private AtomicReference<Integer> mockResponseCode = new AtomicReference<>(200);

    @BeforeEach
    void setUp() throws IOException {
        service = new OpenAiVisionService();
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "timeoutMs", 5000);

        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.createContext("/v1/chat/completions", this::handleChatCompletion);
        mockServer.setExecutor(null);
        mockServer.start();

        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + mockPort);

        lenient().when(configService.getDecrypted("openai.api_key"))
                .thenReturn(Optional.of("sk-test-key"));
        lenient().when(configService.findByClave("openai.modelo"))
                .thenReturn(Optional.of(cs("openai.modelo", "gpt-4o")));
        lenient().when(configService.findByClave("openai.prompt_adicional"))
                .thenReturn(Optional.of(cs("openai.prompt_adicional", "")));
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) mockServer.stop(0);
    }

    @Test
    void analizarImagen_facturaLegal_devuelveDatosParseados() throws Exception {
        mockResponse.set(buildOpenAiResponse(
                "{\"emisorRuc\":\"80012345-6\",\"emisorNombre\":\"FARMA SA\"," +
                "\"numeroFactura\":\"001-001-0001234\",\"timbrado\":\"12345678\"," +
                "\"fechaEmision\":\"2026-05-26\",\"moneda\":\"PYG\",\"totalGeneral\":150000," +
                "\"esLegal\":true,\"items\":[{\"codigoProducto\":\"P001\"," +
                "\"nombreProducto\":\"PARACETAMOL 500MG\",\"cantidad\":2," +
                "\"precioUnitario\":75000,\"descuento\":0,\"totalItem\":150000}]}",
                "gpt-4o", 1234, 567));

        OpenAiVisionService.Resultado r = service.analizarImagen(new byte[]{1, 2, 3}, "image/jpeg");

        assertNotNull(r.data);
        assertEquals("80012345-6", r.data.getEmisorRuc());
        assertEquals("FARMA SA", r.data.getEmisorNombre());
        assertEquals(Boolean.TRUE, r.data.getEsLegal());
        assertEquals(1, r.data.getItems().size());
        assertEquals("PARACETAMOL 500MG", r.data.getItems().get(0).getNombreProducto());
        assertEquals(1234, r.tokensPrompt);
        assertEquals(567, r.tokensRespuesta);
        assertEquals("gpt-4o", r.modeloUsado);
    }

    @Test
    void analizarImagen_notaComun_esLegalFalse() throws Exception {
        mockResponse.set(buildOpenAiResponse(
                "{\"emisorNombre\":\"PROVEEDOR INFORMAL\",\"numeroFactura\":\"123\"," +
                "\"timbrado\":null,\"fechaEmision\":\"2026-05-26\",\"moneda\":\"PYG\"," +
                "\"totalGeneral\":50000,\"esLegal\":false,\"items\":[]}",
                "gpt-4o", 100, 50));

        OpenAiVisionService.Resultado r = service.analizarImagen(new byte[]{1}, "image/jpeg");

        assertEquals(Boolean.FALSE, r.data.getEsLegal());
        assertNull(r.data.getTimbrado());
        assertNull(r.data.getEmisorRuc());
    }

    @Test
    void analizarImagen_noEsDocumentoValido_devuelveError() throws Exception {
        mockResponse.set(buildOpenAiResponse(
                "{\"error\":\"NO_ES_DOCUMENTO_VALIDO\"}", "gpt-4o", 50, 20));

        OpenAiVisionService.Resultado r = service.analizarImagen(new byte[]{1}, "image/jpeg");

        assertEquals("NO_ES_DOCUMENTO_VALIDO", r.data.getError());
    }

    @Test
    void analizarImagen_envia_authorization_bearer_correcto() throws Exception {
        mockResponse.set(buildOpenAiResponse("{\"items\":[]}", "gpt-4o", 10, 5));

        service.analizarImagen(new byte[]{9, 9, 9}, "image/jpeg");

        assertEquals("Bearer sk-test-key", capturedAuth.get());
    }

    @Test
    void analizarImagen_body_contiene_modelo_y_image_url_base64() throws Exception {
        mockResponse.set(buildOpenAiResponse("{\"items\":[]}", "gpt-4o", 10, 5));

        service.analizarImagen(new byte[]{0x41, 0x42, 0x43}, "image/png");

        String body = capturedBody.get();
        assertTrue(body.contains("\"model\":\"gpt-4o\""), "incluye modelo");
        assertTrue(body.contains("\"temperature\":0"), "temperature 0");
        assertTrue(body.contains("json_object"), "JSON mode");
        assertTrue(body.contains("data:image/png;base64,QUJD"), "imagen base64 con mime");
        assertTrue(body.contains("PROMPT_BASE_MARKER_ABSENT") == false, "prompt incluido");
        assertTrue(body.contains("Eres un asistente"), "prompt base presente");
    }

    @Test
    void analizarImagen_promptAdicional_seConcatena() throws Exception {
        when(configService.findByClave("openai.prompt_adicional"))
                .thenReturn(Optional.of(cs("openai.prompt_adicional", "INSTRUCCION EXTRA XYZ")));
        mockResponse.set(buildOpenAiResponse("{\"items\":[]}", "gpt-4o", 10, 5));

        service.analizarImagen(new byte[]{1}, "image/jpeg");

        assertTrue(capturedBody.get().contains("INSTRUCCION EXTRA XYZ"));
    }

    @Test
    void analizarImagen_modeloPorDefecto_gpt4o_siNoConfigurado() throws Exception {
        when(configService.findByClave("openai.modelo")).thenReturn(Optional.empty());
        mockResponse.set(buildOpenAiResponse("{\"items\":[]}", "gpt-4o", 10, 5));

        service.analizarImagen(new byte[]{1}, "image/jpeg");

        assertTrue(capturedBody.get().contains("\"model\":\"gpt-4o\""));
    }

    @Test
    void analizarImagen_apiKeyNoConfigurada_lanzaIOException() {
        when(configService.getDecrypted("openai.api_key")).thenReturn(Optional.empty());

        IOException ex = assertThrows(IOException.class,
                () -> service.analizarImagen(new byte[]{1}, "image/jpeg"));
        assertTrue(ex.getMessage().contains("API key"));
    }

    @Test
    void analizarImagen_imagenVacia_lanzaIOException() {
        assertThrows(IOException.class, () -> service.analizarImagen(new byte[0], "image/jpeg"));
        assertThrows(IOException.class, () -> service.analizarImagen(null, "image/jpeg"));
    }

    @Test
    void analizarImagen_httpError_lanzaIOExceptionConMensaje() {
        mockResponseCode.set(401);
        mockResponse.set("{\"error\":{\"message\":\"Invalid key\"}}");

        IOException ex = assertThrows(IOException.class,
                () -> service.analizarImagen(new byte[]{1}, "image/jpeg"));
        assertTrue(ex.getMessage().contains("401"), "mensaje incluye codigo HTTP: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Invalid key"), "incluye body de error");
    }

    @Test
    void analizarImagen_choicesVacio_lanzaIOException() {
        mockResponse.set("{\"choices\":[]}");

        assertThrows(IOException.class, () -> service.analizarImagen(new byte[]{1}, "image/jpeg"));
    }

    // -- helpers --

    private ConfiguracionSistema cs(String clave, String valor) {
        return new ConfiguracionSistema(clave, valor, "desc", false, LocalDateTime.now());
    }

    private void handleChatCompletion(HttpExchange ex) throws IOException {
        capturedAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
        capturedBody.set(new String(ex.getRequestBody().readAllBytes()));

        byte[] resp = mockResponse.get().getBytes();
        int code = mockResponseCode.get();
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, resp.length);
        ex.getResponseBody().write(resp);
        ex.close();
    }

    private static String defaultOpenAiResponse() {
        return buildOpenAiResponse("{\"items\":[]}", "gpt-4o", 0, 0);
    }

    private static String buildOpenAiResponse(String contentJson, String modelo, int pt, int ct) {
        ObjectMapper m = new ObjectMapper();
        try {
            // Validar que contentJson es JSON parseable
            JsonNode n = m.readTree(contentJson);
            String escaped = m.writeValueAsString(contentJson);
            return "{\"id\":\"x\",\"model\":\"" + modelo + "\"," +
                    "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + escaped + "}}]," +
                    "\"usage\":{\"prompt_tokens\":" + pt + ",\"completion_tokens\":" + ct + "}}";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
