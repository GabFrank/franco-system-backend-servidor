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
}
