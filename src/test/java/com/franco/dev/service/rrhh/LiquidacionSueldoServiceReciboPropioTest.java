package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Issue #346: el autoservicio solo abre el recibo de una liquidacion PAGADA de la persona
 * autenticada. Lo mismo que lista RrhhMobileService.misRecibos.
 */
class LiquidacionSueldoServiceReciboPropioTest {

    private static final Long LIQUIDACION_ID = 4321L;
    private static final Long PERSONA_ID = 987L;

    @Mock private LiquidacionSueldoRepository repository;

    @InjectMocks private LiquidacionSueldoService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void pregunta_por_la_liquidacion_pagada_de_esa_persona() {
        when(repository.existsByIdAndEstadoAndFuncionarioPersonaId(
                LIQUIDACION_ID, LiquidacionSueldoEstado.PAGADA, PERSONA_ID)).thenReturn(true);

        assertTrue(service.esReciboPagadoDe(LIQUIDACION_ID, PERSONA_ID));
    }

    @Test
    void si_no_es_suya_o_no_esta_pagada_no_es_su_recibo() {
        assertFalse(service.esReciboPagadoDe(LIQUIDACION_ID, PERSONA_ID));

        verify(repository).existsByIdAndEstadoAndFuncionarioPersonaId(
                LIQUIDACION_ID, LiquidacionSueldoEstado.PAGADA, PERSONA_ID);
    }

    @Test
    void sin_persona_o_sin_id_no_consulta_y_no_es_su_recibo() {
        assertFalse(service.esReciboPagadoDe(LIQUIDACION_ID, null));
        assertFalse(service.esReciboPagadoDe(null, PERSONA_ID));

        verifyNoInteractions(repository);
    }
}
