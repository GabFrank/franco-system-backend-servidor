package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.AnulacionPagoRrhhService;
import com.franco.dev.service.financiero.ChequeAnuladoTicketService;
import com.franco.dev.service.financiero.ChequeGestionService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChequeAnulacionGraphQLTest {

    private ChequeGestionService gestion;
    private AnulacionPagoRrhhService rrhh;
    private TesoreriaSecurityService seg;
    private RrhhSecurityService rrhhSeg;
    private ChequeAnulacionGraphQL resolver;

    @BeforeEach
    void setUp() {
        gestion = mock(ChequeGestionService.class);
        rrhh = mock(AnulacionPagoRrhhService.class);
        seg = mock(TesoreriaSecurityService.class);
        rrhhSeg = mock(RrhhSecurityService.class);
        resolver = new ChequeAnulacionGraphQL(gestion, rrhh, mock(ChequeAnuladoTicketService.class), seg, rrhhSeg);
    }

    @Test
    void hay_que_indicar_uno_solo() {
        assertThrows(GraphQLException.class, () -> resolver.chequesAAnular(null, null, null));
        assertThrows(GraphQLException.class, () -> resolver.chequesAAnular(1L, 2L, null));
        verifyNoInteractions(gestion);
    }

    @Test
    void un_pago_pide_el_rol_de_la_anulacion_del_pago() {
        resolver.chequesAAnular(600L, null, null);
        verify(seg).requirePagarCppCaja();
        verify(gestion).chequesAAnular(Collections.singletonList(600L));
    }

    @Test
    void al_rol_acotado_no_le_muestra_cheques() {
        when(seg.pagaAcotado()).thenReturn(true);
        assertTrue(resolver.chequesAAnular(600L, null, null).isEmpty());
        verify(gestion, never()).chequesAAnular(any());
    }

    @Test
    void una_liquidacion_pide_el_rol_de_rrhh_y_busca_sus_pagos_en_el_central() {
        when(rrhh.pagosAAnularDeLiquidacion(8L)).thenReturn(Arrays.asList(700L, 701L));
        resolver.chequesAAnular(null, 8L, null);
        verify(rrhhSeg).requireAnyRole(RrhhSecurityService.PAGAR);
        verify(gestion).chequesAAnular(Arrays.asList(700L, 701L));
        verify(seg, never()).requirePagarCppCaja();
    }

    @Test
    void el_comprobante_pide_un_rol_de_tesoreria() {
        doThrow(new GraphQLException("No autorizado")).when(seg).requireVer();
        assertThrows(GraphQLException.class, () -> resolver.imprimirChequeAnulado(5L, 58));
    }
}
