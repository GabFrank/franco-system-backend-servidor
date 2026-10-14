package com.franco.dev.service.financiero;

import com.franco.dev.graphql.financiero.RolCajaSoporte;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * TESORERIA CAJA es un rol acotado: pasa los controles delimitados por caja y ninguno de los amplios.
 * No está en TODOS a propósito, así que requireVer / requireGestionar / requirePagarCpp lo rechazan.
 */
class TesoreriaSecurityServiceRolCajaTest {

    private final RolCajaSoporte s = new RolCajaSoporte();

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
    }

    @Test
    void elRolAcotado_pasaLosControlesDeCaja_yNingunoDeLosAmplios() {
        s.autenticar("gerente", "TESORERIA CAJA");

        s.seg.requireVerCaja();
        s.seg.requireOperarCaja();
        s.seg.requirePagarCppCaja();

        assertThrows(GraphQLException.class, () -> s.seg.requireVer());
        assertThrows(GraphQLException.class, () -> s.seg.requireGestionar());
        assertThrows(GraphQLException.class, () -> s.seg.requirePagarCpp());
        assertThrows(GraphQLException.class, () -> s.seg.requireCobrarCpc());
        assertThrows(GraphQLException.class, () -> s.seg.requireSuperusuario());
    }

    @Test
    void losRolesDeSiempre_siguenIgual() {
        s.autenticar("auditor", "TESORERIA VER");

        s.seg.requireVer();
        s.seg.requireVerCaja();
        assertThrows(GraphQLException.class, () -> s.seg.requireOperarCaja());
        assertThrows(GraphQLException.class, () -> s.seg.requirePagarCppCaja());
        assertFalse(s.seg.esSoloCaja());
    }

    @Test
    void sinRolDeTesoreria_noPasaNinguno() {
        s.autenticar("cajera", "ANALISIS DE CAJA");

        assertThrows(GraphQLException.class, () -> s.seg.requireVerCaja());
        assertThrows(GraphQLException.class, () -> s.seg.requireOperarCaja());
        assertThrows(GraphQLException.class, () -> s.seg.requirePagarCppCaja());
        assertFalse(s.seg.esSoloCaja());
    }

    @Test
    void esSoloCaja_soloConElRolAcotadoYNingunoAmplio() {
        s.autenticar("gerente", "TESORERIA CAJA", "ANALISIS DE CAJA");
        assertTrue(s.seg.esSoloCaja());
    }

    @Test
    void esSoloCaja_elRolAmplioManda() {
        for (String amplio : TesoreriaSecurityService.TODOS) {
            s.autenticar("tesorero", "TESORERIA CAJA", amplio);
            assertFalse(s.seg.esSoloCaja(), amplio);
        }
    }

    @Test
    void esSoloCaja_elSuperusuarioNoEsAcotado() {
        s.autenticar("jefe", "TESORERIA CAJA", "ADMIN");
        assertFalse(s.seg.esSoloCaja());

        s.autenticar("ADMIN");
        assertFalse(s.seg.esSoloCaja());
    }

    @Test
    void esSoloCaja_sinUsuarioAutenticadoEsFalso() {
        // Un scheduler o la replicación de retiros: no es un operador acotado.
        assertFalse(s.seg.esSoloCaja());
    }

    @Test
    void sinNingunaCaja_lasListasSonVaciasYNoNulas() {
        // null significa «ve todas»: un acotado sin cajas no puede caer ahí.
        s.autenticar("gerente", "TESORERIA CAJA");
        when(s.accesoRepository.cajasVisiblesIds(RolCajaSoporte.USUARIO_ID)).thenReturn(Collections.emptyList());
        when(s.accesoRepository.sucursalesOperablesIds(RolCajaSoporte.USUARIO_ID)).thenReturn(Collections.emptyList());

        assertNotNull(s.seg.cajasVisiblesIds());
        assertTrue(s.seg.cajasVisiblesIds().isEmpty());
        assertNotNull(s.seg.sucursalesOperablesIds());
        assertTrue(s.seg.sucursalesOperablesIds().isEmpty());
    }

    @Test
    void sucursalesOperables_salenDelRepositorio_ySinUsuarioEsVacia() {
        assertTrue(s.seg.sucursalesOperablesIds().isEmpty());

        s.autenticar("gerente", "TESORERIA CAJA");
        when(s.accesoRepository.sucursalesOperablesIds(RolCajaSoporte.USUARIO_ID)).thenReturn(Arrays.asList(13L, 4L));
        assertEquals(Arrays.asList(13L, 4L), s.seg.sucursalesOperablesIds());
    }

    // ── Acotado por acción: sumar un rol no puede dar las operaciones de GESTIONAR ──

    @Test
    void conSoloCaja_operaYPagaAcotado() {
        s.autenticar("gerente", "TESORERIA CAJA");
        assertTrue(s.seg.operaAcotado());
        assertTrue(s.seg.pagaAcotado());
    }

    @Test
    void cajaMasUnRolDeLectura_noEsSoloCajaPeroSigueOperandoYPagandoAcotado() {
        for (String rol : new String[]{"TESORERIA VER", "TESORERIA CPC COBRAR"}) {
            s.autenticar("gerente", "TESORERIA CAJA", rol);
            assertFalse(s.seg.esSoloCaja(), rol);
            assertTrue(s.seg.operaAcotado(), rol);
            assertTrue(s.seg.pagaAcotado(), rol);
        }
    }

    @Test
    void cajaMasCppPagar_pagaComoCppPagarPeroOperaAcotado() {
        s.autenticar("tesorero", "TESORERIA CAJA", "TESORERIA CPP PAGAR");
        assertFalse(s.seg.pagaAcotado());
        assertTrue(s.seg.operaAcotado());
    }

    @Test
    void conGestionarOSuperusuario_nadaEsAcotado() {
        s.autenticar("tesorero", "TESORERIA CAJA", "TESORERIA GESTIONAR");
        assertFalse(s.seg.operaAcotado());
        assertFalse(s.seg.pagaAcotado());

        s.autenticar("jefe", "TESORERIA CAJA", "ADMIN");
        assertFalse(s.seg.operaAcotado());
        assertFalse(s.seg.pagaAcotado());
    }

    @Test
    void sinElRolDeCaja_oSinUsuario_nadaEsAcotado() {
        assertFalse(s.seg.operaAcotado());
        assertFalse(s.seg.pagaAcotado());

        s.autenticar("auditor", "TESORERIA VER");
        assertFalse(s.seg.operaAcotado());
        assertFalse(s.seg.pagaAcotado());
    }

    @Test
    void puedeVerCajaEnRequest_usaLasCajasVisibles() {
        s.autenticar("gerente", "TESORERIA CAJA");
        when(s.accesoRepository.cajasVisiblesIds(RolCajaSoporte.USUARIO_ID)).thenReturn(Arrays.asList(15L));

        assertTrue(s.seg.puedeVerCajaEnRequest(15L));
        assertFalse(s.seg.puedeVerCajaEnRequest(16L));
        assertFalse(s.seg.puedeVerCajaEnRequest(null));

        s.autenticar("jefe", "ADMIN");
        assertTrue(s.seg.puedeVerCajaEnRequest(16L));
    }

    // ── Enviar plata a una caja: poder moverla, o que reciba envíos ──

    private com.franco.dev.domain.financiero.CajaVirtual caja(long id, Boolean activa, Boolean recibeEnvios) {
        com.franco.dev.domain.financiero.CajaVirtual c = new com.franco.dev.domain.financiero.CajaVirtual();
        c.setId(id);
        c.setActivo(activa);
        c.setRecibeEnvios(recibeEnvios);
        when(s.cajaVirtualRepository.findById(id)).thenReturn(java.util.Optional.of(c));
        return c;
    }

    @Test
    void sePuedeEnviarAUnaCajaQueRecibeEnvios_sinTenerAccesoSobreElla() {
        s.autenticar("gerente", "TESORERIA CAJA");
        caja(2L, true, true);

        s.seg.requireEnvioACaja(2L);

        // Enviarle no es poder moverla ni verla.
        assertThrows(GraphQLException.class, () -> s.seg.requireEscrituraCaja(2L));
        assertThrows(GraphQLException.class, () -> s.seg.requireLecturaCaja(2L));
    }

    @Test
    void noSePuedeEnviarAUnaCajaSinLaMarca_niInactiva_niInexistente() {
        s.autenticar("gerente", "TESORERIA CAJA");
        caja(2L, true, false);
        caja(3L, true, null);
        caja(4L, false, true);

        assertThrows(GraphQLException.class, () -> s.seg.requireEnvioACaja(2L));
        assertThrows(GraphQLException.class, () -> s.seg.requireEnvioACaja(3L));
        assertThrows(GraphQLException.class, () -> s.seg.requireEnvioACaja(4L));
        assertThrows(GraphQLException.class, () -> s.seg.requireEnvioACaja(99L));
        assertThrows(GraphQLException.class, () -> s.seg.requireEnvioACaja(null));
    }

    @Test
    void conEscrituraSobreLaCajaSePuedeEnviar_aunqueNoTengaLaMarca() {
        com.franco.dev.domain.personas.Usuario u = s.autenticar("gerente", "TESORERIA CAJA");
        caja(5L, true, false).setUsuario(u);   // es su responsable

        s.seg.requireEnvioACaja(5L);
    }

    @Test
    void sucursalesDeCaja_salenDelRepositorio_yNuncaSonNull() {
        when(s.accesoRepository.sucursalesDeCajaIds(19L)).thenReturn(Arrays.asList(12L, 9L, 23L));
        when(s.accesoRepository.sucursalesDeCajaIds(20L)).thenReturn(null);

        assertEquals(Arrays.asList(12L, 9L, 23L), s.seg.sucursalesDeCajaIds(19L));
        assertTrue(s.seg.sucursalesDeCajaIds(20L).isEmpty());
        assertTrue(s.seg.sucursalesDeCajaIds(null).isEmpty());
    }
}
