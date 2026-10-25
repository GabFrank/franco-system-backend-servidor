package com.franco.dev.service.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.FrancoSystemsApplication;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FacturaLegalFilialService.EnvioAFilialException;
import com.franco.dev.service.financiero.dto.FacturaLegalFilialResponse;
import com.franco.dev.service.personas.ClienteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * #425: cada fallo al emitir en el filial dice si la factura pudo haberse emitido. Los mensajes son los de antes,
 * byte a byte: el desktop sin el código los reconoce por el texto.
 */
class FacturaLegalFilialServiceErroresTest {

    @Mock private RestTemplate restTemplate;
    @Mock private SucursalService sucursalService;
    @Mock private ClienteService clienteService;

    private FacturaLegalFilialService service;
    private Sucursal sucursal;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new FacturaLegalFilialService();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(service, "clienteService", clienteService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        sucursal = new Sucursal();
        sucursal.setId(24L);
        sucursal.setNombre("PRUEBA");
        sucursal.setIp("127.0.0.1");
        sucursal.setPuertoServidor(8082);
        when(sucursalService.findById(24L)).thenReturn(Optional.of(sucursal));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private EnvioAFilialException enviar() {
        return assertThrows(EnvioAFilialException.class,
                () -> service.crearFacturaLegalEnFilial(new FacturaLegalInput(), new ArrayList<>(), 24L, 7L));
    }

    private void alEnviar(Throwable t) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(FacturaLegalFilialResponse.class))).thenThrow(t);
    }

    @Test
    void sucursalInexistenteEsRechazo() {
        when(sucursalService.findById(anyLong())).thenReturn(Optional.empty());
        EnvioAFilialException e = enviar();
        assertEquals("Sucursal no encontrada: 24", e.getMessage());
        assertFalse(e.isSinConfirmar());
    }

    @Test
    void sucursalSinIpEsRechazo() {
        sucursal.setIp(null);
        EnvioAFilialException e = enviar();
        assertEquals("La sucursal no tiene IP configurada", e.getMessage());
        assertFalse(e.isSinConfirmar());
    }

    @Test
    void errorAlArmarElPedidoEsRechazo() {
        FacturaLegalInput input = new FacturaLegalInput();
        input.setClienteId(5L);
        when(clienteService.findById(5L)).thenThrow(new IllegalStateException("base caida"));
        EnvioAFilialException e = assertThrows(EnvioAFilialException.class,
                () -> service.crearFacturaLegalEnFilial(input, new ArrayList<>(), 24L, 7L));
        assertEquals("Error al preparar la petición al servidor filial: base caida", e.getMessage());
        assertFalse(e.isSinConfirmar());
    }

    @Test
    void cuatroCientosDelFilialEsRechazo() {
        alEnviar(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                "{\"mensaje\":\"El timbrado detalle no está disponible: TIMBRADO_SIN_TELEFONO\"}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        EnvioAFilialException e = enviar();
        assertEquals("Error al crear factura legal: El timbrado detalle no está disponible: TIMBRADO_SIN_TELEFONO",
                e.getMessage());
        assertFalse(e.isSinConfirmar());
    }

    @Test
    void quinientosDelFilialEsSinConfirmar() {
        alEnviar(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                new HttpHeaders(), "{\"mensaje\":\"could not execute statement\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));
        EnvioAFilialException e = enviar();
        assertEquals("Error del servidor filial: could not execute statement", e.getMessage());
        assertTrue(e.isSinConfirmar());
    }

    @Test
    void conexionRechazadaEsRechazoAunqueVengaAnidada() {
        alEnviar(new ResourceAccessException("I/O error on POST request: Connection refused",
                new java.io.IOException("envoltorio", new ConnectException("Connection refused"))));
        EnvioAFilialException e = enviar();
        assertEquals("Error al conectar con el servidor filial: I/O error on POST request: Connection refused;"
                + " nested exception is java.io.IOException: envoltorio", e.getMessage());
        assertFalse(e.isSinConfirmar());
    }

    @Test
    void cortePorTiempoEsSinConfirmar() {
        alEnviar(new ResourceAccessException("I/O error on POST request: Read timed out",
                new SocketTimeoutException("Read timed out")));
        EnvioAFilialException e = enviar();
        assertEquals("Error al conectar con el servidor filial: I/O error on POST request: Read timed out;"
                + " nested exception is java.net.SocketTimeoutException: Read timed out", e.getMessage());
        assertTrue(e.isSinConfirmar());
    }

    @Test
    void respuestaIlegibleEsSinConfirmar() {
        alEnviar(new RestClientException("Error while extracting response"));
        EnvioAFilialException e = enviar();
        assertEquals("Error al conectar con el servidor filial: Error while extracting response", e.getMessage());
        assertTrue(e.isSinConfirmar());
    }

    @Test
    void respuestaQueNoEs201EsSinConfirmar() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(FacturaLegalFilialResponse.class))).thenReturn(ResponseEntity.ok(new FacturaLegalFilialResponse()));
        EnvioAFilialException e = enviar();
        assertEquals("Error al crear factura en servidor filial. Código: 200 OK", e.getMessage());
        assertTrue(e.isSinConfirmar());
    }

    @Test
    void creadoSinCuerpoEsSinConfirmar() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(FacturaLegalFilialResponse.class))).thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
        assertTrue(enviar().isSinConfirmar());
    }

    // Con el RestTemplate del bean y sockets de verdad: lo que el JDK hace con el POST.

    private void conRestTemplateReal(int puerto) {
        ReflectionTestUtils.setField(service, "restTemplate", new FrancoSystemsApplication().getResTemplate());
        sucursal.setPuertoServidor(puerto);
    }

    @Test
    void puertoCerradoDeVerdadEsRechazo() throws Exception {
        int puerto;
        try (ServerSocket s = new ServerSocket(0)) {
            puerto = s.getLocalPort();
        }
        conRestTemplateReal(puerto);
        assertFalse(enviar().isSinConfirmar());
    }

    @Test
    void filialQueRecibeYCortaEsSinConfirmarYNoSeReenvia() throws Exception {
        AtomicInteger conexiones = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5_000);
            Thread filial = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) {
                        conexiones.incrementAndGet();
                        leerPedido(s);
                        // cierra sin responder: el filial pudo haber guardado
                    } catch (Exception ignorada) {
                        return;
                    }
                }
            });
            filial.start();
            conRestTemplateReal(server.getLocalPort());

            assertTrue(enviar().isSinConfirmar());
            Thread.sleep(300);
            assertEquals(1, conexiones.get(), "el POST no se reintenta por dentro");
        }
    }

    private static void leerPedido(Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        int largo = 0;
        String linea;
        while ((linea = in.readLine()) != null && !linea.isEmpty()) {
            if (linea.toLowerCase().startsWith("content-length:")) {
                largo = Integer.parseInt(linea.substring(15).trim());
            }
        }
        for (int i = 0; i < largo; i++) {
            in.read();
        }
    }
}
