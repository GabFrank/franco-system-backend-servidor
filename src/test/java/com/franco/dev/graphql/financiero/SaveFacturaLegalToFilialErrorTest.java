package com.franco.dev.graphql.financiero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.exceptions.GraphqlExceptionHandler;
import com.franco.dev.graphql.financiero.input.FacturaLegalInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FacturaLegalFilialService;
import com.franco.dev.service.financiero.dto.FacturaLegalFilialResponse;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.GraphqlErrorException;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * #425: el error de saveFacturaLegalToFilial lleva en extensions.codigo si la factura pudo haberse emitido, con el
 * mismo mensaje de antes. Usa el servicio real (solo el RestTemplate es simulado) para fallar por comportamiento
 * con el código anterior.
 */
class SaveFacturaLegalToFilialErrorTest {

    private static final String PREFIJO = "Error al crear factura en servidor filial: ";

    @Mock private RestTemplate restTemplate;
    @Mock private SucursalService sucursalService;

    @InjectMocks private FacturaLegalGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        FacturaLegalFilialService filialService = new FacturaLegalFilialService();
        ReflectionTestUtils.setField(filialService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(filialService, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(filialService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(resolver, "facturaLegalFilialService", filialService);

        Sucursal sucursal = new Sucursal();
        sucursal.setId(24L);
        sucursal.setIp("127.0.0.1");
        when(sucursalService.findById(24L)).thenReturn(Optional.of(sucursal));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private GraphqlErrorException guardar() {
        return assertThrows(GraphqlErrorException.class, () -> resolver.saveFacturaLegalToFilial(
                new FacturaLegalInput(), new ArrayList<>(), 24L, 7L, null, null));
    }

    private void filialResponde(Throwable t) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(FacturaLegalFilialResponse.class))).thenThrow(t);
    }

    @Test
    void rechazoDelFilialLlevaFilialRechazo() {
        filialResponde(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                "{\"mensaje\":\"TIMBRADO_SIN_TELEFONO\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        GraphqlErrorException e = guardar();
        assertEquals(PREFIJO + "Error al crear factura legal: TIMBRADO_SIN_TELEFONO", e.getMessage());
        assertEquals("FILIAL_RECHAZO", e.getExtensions().get("codigo"));
    }

    @Test
    void quinientosDelFilialLlevaFilialSinConfirmar() {
        filialResponde(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));
        assertEquals("FILIAL_SIN_CONFIRMAR", guardar().getExtensions().get("codigo"));
    }

    @Test
    void errorNoClasificadoCaeEnSinConfirmar() {
        when(sucursalService.findById(24L)).thenThrow(new IllegalStateException("pool agotado"));
        GraphqlErrorException e = guardar();
        assertEquals(PREFIJO + "pool agotado", e.getMessage());
        assertEquals("FILIAL_SIN_CONFIRMAR", e.getExtensions().get("codigo"));
    }

    /** Lo que ve el cliente: el handler desanida el error, sin el prefijo de graphql-java y con el código. */
    @Test
    @SuppressWarnings("unchecked")
    void llegaAlClienteSinPrefijoYConElCodigo() {
        filialResponde(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                "{\"mensaje\":\"TIMBRADO_SIN_TELEFONO\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        GraphqlErrorException e = guardar();

        GraphQLError envuelto = new ExceptionWhileDataFetching(ResultPath.parse("/data"), e, new SourceLocation(1, 1));
        List<GraphQLError> salida = new GraphqlExceptionHandler().processErrors(Collections.singletonList(envuelto));
        Map<String, Object> json = salida.get(0).toSpecification();

        assertEquals(PREFIJO + "Error al crear factura legal: TIMBRADO_SIN_TELEFONO", json.get("message"));
        Map<String, Object> extensions = (Map<String, Object>) json.get("extensions");
        assertEquals("FILIAL_RECHAZO", extensions.get("codigo"));
        assertEquals("DataFetchingException", extensions.get("classification"));
    }
}
