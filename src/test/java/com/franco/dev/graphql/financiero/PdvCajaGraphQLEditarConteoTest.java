package com.franco.dev.graphql.financiero;

import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.service.financiero.FilialCajaProxyService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * editarConteoCajaDesdeServidor tiene que devolver el id del conteo que creo la filial. El desktop lo
 * usa como conteoAnteriorId de la siguiente edicion: sin el, re-editar la misma caja sin reabrirla
 * quedaba en un boton "Guardar edicion" que no hacia nada.
 */
class PdvCajaGraphQLEditarConteoTest {

    private static final Long CAJA = 50L;
    private static final Long SUCURSAL = 3L;
    private static final Long USUARIO = 7L;
    private static final Long CONTEO_ANTERIOR = 10L;
    private static final Long CONTEO_NUEVO = 99L;

    @Mock private PdvCajaService service;
    @Mock private UsuarioService usuarioService;
    @Mock private FilialCajaProxyService filialCajaProxyService;

    @InjectMocks private PdvCajaGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(usuarioService.tieneRol(USUARIO, "ADMIN")).thenReturn(true);
        when(service.findById(CAJA, SUCURSAL)).thenReturn(null);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private ConteoInput conteoInput() {
        ConteoInput input = new ConteoInput();
        input.setUsuarioId(USUARIO);
        return input;
    }

    @Test
    void devuelve_el_id_del_conteo_creado_en_la_filial() throws Exception {
        List<ConteoMonedaInput> monedas = Collections.singletonList(new ConteoMonedaInput());
        when(filialCajaProxyService.editarConteoEnFilial(eq(CAJA), eq(SUCURSAL), eq(CONTEO_ANTERIOR), eq(true),
                any(), any(), eq(USUARIO))).thenReturn(CONTEO_NUEVO);

        CajaFilialOperacionResult result = resolver.editarConteoCajaDesdeServidor(CAJA, SUCURSAL, CONTEO_ANTERIOR,
                true, conteoInput(), monedas);

        assertTrue(result.getExito());
        assertEquals(CAJA, result.getCajaId());
        assertEquals(CONTEO_NUEVO, result.getConteoId(),
                "sin el id nuevo el cliente no puede volver a editar el conteo que acaba de corregir");
    }

    @Test
    void timeout_con_la_filial_no_afirma_que_no_se_guardo() throws Exception {
        when(filialCajaProxyService.editarConteoEnFilial(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Read timed out"));

        Exception e = assertThrows(Exception.class, () -> resolver.editarConteoCajaDesdeServidor(CAJA, SUCURSAL,
                CONTEO_ANTERIOR, true, conteoInput(), Collections.singletonList(new ConteoMonedaInput())));

        assertFalse(e.getMessage().contains("No se modifico"),
                "la filial pudo haber confirmado la edicion antes del timeout");
    }
}
