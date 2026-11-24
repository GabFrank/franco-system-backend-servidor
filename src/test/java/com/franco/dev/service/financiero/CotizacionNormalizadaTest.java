package com.franco.dev.service.financiero;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Cotización «menor por 1 mayor» (issue #603), con los casos reales de bodega y de la copia local. */
class CotizacionNormalizadaTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void guaranies_a_real_coherente_se_conserva_tal_cual() {
        // 2.000.000 Gs → 1.777,78 R$ a 1.125: cierra (la de los montos da 1.124,9986).
        CotizacionNormalizada n = CotizacionNormalizada.de(bd("2000000"), bd("1777.78"), bd("1125"));
        assertFalse(n.mayorEsOrigen, "el real vale más que el guaraní");
        assertTrue(n.recibidaCoincide);
        assertEquals(bd("1125.000000"), n.cotizacion);
    }

    @Test
    void real_a_dolar_invertida_se_invierte() {
        // 560 R$ → 102,20 US$ con 0,1825 (US$ por R$): la normalizada es R$ por 1 US$.
        CotizacionNormalizada n = CotizacionNormalizada.de(bd("560"), bd("102.20"), bd("0.1825"));
        assertFalse(n.mayorEsOrigen, "el dólar vale más que el real");
        assertFalse(n.recibidaCoincide);
        assertEquals(bd("5.479452"), n.cotizacion);
    }

    @Test
    void real_a_dolar_ya_en_formato_casa_de_cambio_se_conserva() {
        CotizacionNormalizada n = CotizacionNormalizada.de(bd("560"), bd("100"), bd("5.60"));
        assertTrue(n.recibidaCoincide);
        assertEquals(bd("5.600000"), n.cotizacion);
    }

    @Test
    void dolar_a_guaranies_con_el_mayor_en_el_origen() {
        CotizacionNormalizada n = CotizacionNormalizada.de(bd("100"), bd("640000"), bd("6400"));
        assertTrue(n.mayorEsOrigen);
        assertTrue(n.recibidaCoincide);
    }

    @Test
    void sin_cotizacion_o_que_no_cierra_se_usa_la_de_los_montos() {
        assertEquals(bd("5.600000"), CotizacionNormalizada.de(bd("560"), bd("100"), null).cotizacion);
        CotizacionNormalizada n = CotizacionNormalizada.de(bd("560"), bd("100"), bd("7.10"));
        assertFalse(n.recibidaCoincide);
        assertEquals(bd("5.600000"), n.cotizacion);
    }

    @Test
    void sin_dos_montos_positivos_no_decide() {
        assertNull(CotizacionNormalizada.de(null, bd("100"), bd("5.6")));
        assertNull(CotizacionNormalizada.de(bd("0"), bd("100"), bd("5.6")));
    }
}
