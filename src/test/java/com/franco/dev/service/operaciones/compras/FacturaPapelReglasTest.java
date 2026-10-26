package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FacturaPapelReglasTest {

    @Test
    void rucDeGenfiEsValido() {
        // RUC+DV reales sacados de CDC de SIFEN (proveedores de bodega3)
        assertTrue(FacturaPapelReglas.rucValido("80062507-2"));
        assertTrue(FacturaPapelReglas.rucValido("80069563-1"));
        assertTrue(FacturaPapelReglas.rucValido("80099482-5"));
        assertEquals("80062507", FacturaPapelReglas.sinDv("80062507-2"));
        assertEquals("4763946", FacturaPapelReglas.sinDv("4763946"));
    }

    @Test
    void rucConUnDigitoCambiadoNoEsValido() {
        assertFalse(FacturaPapelReglas.rucValido("80062507-3"));
        assertFalse(FacturaPapelReglas.rucValido("80062508-2"));
        assertFalse(FacturaPapelReglas.rucValido("80062507"));
        assertFalse(FacturaPapelReglas.rucValido(null));
        assertFalse(FacturaPapelReglas.rucValido("abc-1"));
    }

    @Test
    void numeroConCerosFaltantesSeRellena() {
        assertFalse(FacturaPapelReglas.numeroValido("1", "1", "1234"));
        assertTrue(FacturaPapelReglas.numeroValido(FacturaPapelReglas.rellenar("1", 3),
                FacturaPapelReglas.rellenar("1", 3), FacturaPapelReglas.rellenar("1234", 7)));
        assertEquals("0001234", FacturaPapelReglas.rellenar("1234", 7));
        assertNull(FacturaPapelReglas.rellenar("12345678", 7));
        assertNull(FacturaPapelReglas.rellenar("12a", 7));
        assertNull(FacturaPapelReglas.rellenar(" ", 7));
        assertNull(FacturaPapelReglas.rellenar(null, 7));
    }

    @Test
    void timbradoDeSieteDigitosNoEsValido() {
        assertFalse(FacturaPapelReglas.timbradoValido(1234567));
        assertFalse(FacturaPapelReglas.timbradoValido(null));
        assertTrue(FacturaPapelReglas.timbradoValido(12345678));
    }

    @Test
    void totalesCuadranConRedondeo() {
        assertTrue(FacturaPapelReglas.totalesCuadran(100000, Arrays.asList(33333.0, 33333.0, 33333.0)));
        assertTrue(FacturaPapelReglas.totalesCuadran(1000, Arrays.asList(1000.5)));
    }

    @Test
    void totalesNoCuadranMasDeUnoPorCiento() {
        assertFalse(FacturaPapelReglas.totalesCuadran(100000, Arrays.asList(50000.0, 48000.0)));
    }
}
