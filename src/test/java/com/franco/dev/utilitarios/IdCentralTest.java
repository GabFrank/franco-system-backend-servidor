package com.franco.dev.utilitarios;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdCentralTest {

    @Test
    void sinFilasPreviasArrancaEnUno() {
        assertEquals(1L, IdCentral.siguienteImpar(null));
        assertEquals(1L, IdCentral.siguienteImpar(0L));
    }

    @Test
    void siempreDevuelveUnImparMayorQueElMaximo() {
        assertEquals(7065L, IdCentral.siguienteImpar(7064L), "el maximo par vino del filial");
        assertEquals(7067L, IdCentral.siguienteImpar(7065L), "el maximo impar lo genero el central");
    }

    @Test
    void losParesSonDelFilial() {
        assertTrue(IdCentral.esDeFilial(7064L));
        assertFalse(IdCentral.esDeFilial(7065L));
        assertFalse(IdCentral.esDeFilial(null));
    }
}
