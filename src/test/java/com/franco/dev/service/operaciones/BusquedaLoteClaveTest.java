package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Clave de busqueda de lote (#429 fase 2). Es la gemela Java de operaciones.clave_busqueda_lote
 * (V255.6): si cambia una, cambia la otra.
 */
class BusquedaLoteClaveTest {

    @Test
    void cerosALaIzquierdaNoImportan() {
        assertEquals("3497", BusquedaLoteService.claveBusqueda("3497"));
        assertEquals("3497", BusquedaLoteService.claveBusqueda("03497"));
        assertEquals("3497", BusquedaLoteService.claveBusqueda("0003497"));
    }

    @Test
    void espaciosSeparadoresYMinusculasNoImportan() {
        assertEquals("3497", BusquedaLoteService.claveBusqueda(" 03 497 "));
        assertEquals("3497", BusquedaLoteService.claveBusqueda("03-4.97"));
        assertEquals("L2610A", BusquedaLoteService.claveBusqueda("l2610a"));
        assertEquals("LOTE3497", BusquedaLoteService.claveBusqueda("Lote 3497"));
    }

    @Test
    void todoCerosNoQuedaVacio() {
        assertEquals("000", BusquedaLoteService.claveBusqueda("000"));
        assertEquals("0", BusquedaLoteService.claveBusqueda("0"));
    }

    @Test
    void sinLetrasNiDigitosNoHayClave() {
        assertNull(BusquedaLoteService.claveBusqueda(null));
        assertNull(BusquedaLoteService.claveBusqueda("   "));
        assertNull(BusquedaLoteService.claveBusqueda("- . /"));
    }
}
