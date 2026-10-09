package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.financiero.AnulacionPagoRrhhService;
import com.franco.dev.service.rrhh.LiquidacionSueldoService;
import com.franco.dev.service.rrhh.ReciboLiquidacionService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Issue #346: el recibo de sueldo se baja con rol RRHH o, sin rol, solo si es de una
 * liquidacion pagada del propio funcionario (autoservicio de la PWA). Antes cualquier
 * usuario autenticado bajaba el de otro recorriendo ids.
 */
class LiquidacionSueldoGraphQLReciboSeguridadTest {

    private static final Long LIQUIDACION_ID = 4321L;
    private static final Long PERSONA_ID = 987L;

    @Mock private RrhhSecurityService seg;
    @Mock private LiquidacionSueldoService service;
    @Mock private ReciboLiquidacionService reciboLiquidacionService;
    @Mock private AnulacionPagoRrhhService anulacionPagoRrhhService;

    @InjectMocks private LiquidacionSueldoGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(reciboLiquidacionService.generarBase64(LIQUIDACION_ID, null, false)).thenReturn("recibo");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void rechazaSinGenerar() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.imprimirReciboLiquidacion(LIQUIDACION_ID, null, null));
        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        verify(reciboLiquidacionService, never()).generarBase64(any(), any(), anyBoolean());
    }

    @Test
    void con_rol_rrhh_se_genera_sin_mirar_de_quien_es() {
        when(seg.hasAnyRole(any())).thenReturn(true);

        assertEquals("recibo", resolver.imprimirReciboLiquidacion(LIQUIDACION_ID, null, null));

        verify(service, never()).esReciboPagadoDe(any(), any());
    }

    @Test
    void sin_rol_el_dueno_baja_el_recibo_de_su_liquidacion_pagada() {
        when(seg.hasAnyRole(any())).thenReturn(false);
        when(seg.currentPersonaId()).thenReturn(PERSONA_ID);
        when(service.esReciboPagadoDe(LIQUIDACION_ID, PERSONA_ID)).thenReturn(true);

        assertEquals("recibo", resolver.imprimirReciboLiquidacion(LIQUIDACION_ID, null, null));
    }

    @Test
    void sin_rol_el_recibo_ajeno_o_no_pagado_se_rechaza_sin_generarlo() {
        when(seg.hasAnyRole(any())).thenReturn(false);
        when(seg.currentPersonaId()).thenReturn(PERSONA_ID);
        when(service.esReciboPagadoDe(LIQUIDACION_ID, PERSONA_ID)).thenReturn(false);

        rechazaSinGenerar();
    }

    @Test
    void sin_rol_y_sin_persona_se_rechaza_sin_generarlo() {
        when(seg.hasAnyRole(any())).thenReturn(false);
        when(seg.currentPersonaId()).thenReturn(null);

        rechazaSinGenerar();
        verify(service).esReciboPagadoDe(LIQUIDACION_ID, null);
    }
}
