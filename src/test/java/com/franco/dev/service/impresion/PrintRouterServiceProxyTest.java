package com.franco.dev.service.impresion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.ImpresoraService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El central reenvia la impresion a la filial dueña; un rechazo por rol tiene que llegar con su motivo. */
class PrintRouterServiceProxyTest {

    private RestTemplate rest;
    private PrintRouterService service;

    @BeforeEach
    void setUp() {
        Sucursal km2 = new Sucursal();
        km2.setId(24L);
        km2.setNombre("SUC. KM2");
        km2.setIp("10.0.0.24");
        Impresora impresora = new Impresora();
        impresora.setId(4L);
        impresora.setSucursal(km2);
        ImpresoraService impresoras = mock(ImpresoraService.class);
        when(impresoras.findById(4L)).thenReturn(Optional.of(impresora));
        rest = mock(RestTemplate.class);

        service = new PrintRouterService();
        ReflectionTestUtils.setField(service, "miSucursalId", 0L);
        ReflectionTestUtils.setField(service, "impresoraService", impresoras);
        ReflectionTestUtils.setField(service, "restTemplate", rest);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void filialResponde(Map<String, Object> cuerpo) {
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(cuerpo));
    }

    @Test
    void unRechazoPorRolDeLaFilialLlegaConLaSucursalYElMotivo() {
        filialResponde(Map.of("errors", List.of(Map.of("message",
                "No autorizado: se requiere el rol ANALISIS DE CAJA o CONFIGURACION."))));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.imprimir(4L, new byte[]{1}));

        assertEquals("Sucursal SUC. KM2: No autorizado: se requiere el rol ANALISIS DE CAJA o CONFIGURACION.",
                e.getMessage());
    }

    @Test
    void otroErrorDeLaFilialSigueDevolviendoFalse() {
        filialResponde(Map.of("errors", List.of(Map.of("message", "Validation error of type FieldUndefined"))));

        assertFalse(service.imprimir(4L, new byte[]{1}));
    }

    @Test
    void unErrorsMalFormadoNoRompeYDevuelveFalse() {
        filialResponde(Map.of("errors", "texto suelto"));
        assertFalse(service.imprimir(4L, new byte[]{1}));

        filialResponde(Map.of("errors", List.of("sin message", Map.of("otro", 1))));
        assertFalse(service.imprimir(4L, new byte[]{1}));
    }

    @Test
    void laImpresionConfirmadaDevuelveTrue() {
        filialResponde(Map.of("data", Map.of("imprimirEnDestino", true)));

        assertTrue(service.imprimir(4L, new byte[]{1}));
    }

    @Test
    void unTokenQueLaFilialNoAceptaSeInformaComoConfiguracion() {
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.imprimir(4L, new byte[]{1}));

        assertTrue(e.getMessage().startsWith("Sucursal SUC. KM2: no aceptó la sesión del central"), e.getMessage());
    }
}
