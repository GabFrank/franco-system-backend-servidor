package com.franco.dev.service.productos.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostoMedioCalculatorTest {

    private static final double DELTA = 0.001;

    // --- aGuaranies ---

    @Test
    void aGuaranies_cotizacion1_noConvierte() {
        assertEquals(2685.0, CostoMedioCalculator.aGuaranies(2685.0, 1.0), DELTA);
    }

    @Test
    void aGuaranies_monedaExtranjera_multiplicaPorCotizacion() {
        // USD 10 a 7200 Gs/USD
        assertEquals(72000.0, CostoMedioCalculator.aGuaranies(10.0, 7200.0), DELTA);
    }

    @Test
    void aGuaranies_cotizacionMenorA1_noConvierte() {
        assertEquals(500.0, CostoMedioCalculator.aGuaranies(500.0, 0.5), DELTA);
    }

    // --- calcular: ponderación con base válida ---

    @Test
    void calcular_stockPositivoConCostoPrevio_promedioPonderado() {
        // stock anterior = 5 (15 real - 10 entrada), costo medio ant 100, compra 10 @ 120
        // (5*100 + 10*120) / 15 = 1700 / 15 = 113.333
        double r = CostoMedioCalculator.calcular(15.0, 10.0, 100.0, 120.0);
        assertEquals(1700.0 / 15.0, r, DELTA);
    }

    @Test
    void calcular_mismoCosto_quedaIgual() {
        // comprar al mismo costo medio no lo mueve
        double r = CostoMedioCalculator.calcular(20.0, 10.0, 100.0, 100.0);
        assertEquals(100.0, r, DELTA);
    }

    // --- calcular: casos sin base válida → costo de la compra ---

    @Test
    void calcular_stockAnteriorNegativo_reseteaAlCostoCompra() {
        // stock real 5, entrada 10 → stock anterior -5 → no se puede ponderar
        double r = CostoMedioCalculator.calcular(5.0, 10.0, 100.0, 120.0);
        assertEquals(120.0, r, DELTA);
    }

    @Test
    void calcular_stockAnteriorCero_reseteaAlCostoCompra() {
        // stock real 10, entrada 10 → stock anterior 0 → reset
        double r = CostoMedioCalculator.calcular(10.0, 10.0, 100.0, 120.0);
        assertEquals(120.0, r, DELTA);
    }

    @Test
    void calcular_sinCostoPrevio_reseteaAlCostoCompra() {
        double r = CostoMedioCalculator.calcular(15.0, 10.0, null, 120.0);
        assertEquals(120.0, r, DELTA);
    }

    @Test
    void calcular_costoPrevioCero_reseteaAlCostoCompra() {
        double r = CostoMedioCalculator.calcular(15.0, 10.0, 0.0, 120.0);
        assertEquals(120.0, r, DELTA);
    }

    // --- cambio: dedup ---

    @Test
    void cambio_sinRegistroPrevio_devuelveTrue() {
        assertTrue(CostoMedioCalculator.cambio(null, null, null, 100.0, 100.0, 1L));
    }

    @Test
    void cambio_todoIgual_devuelveFalse() {
        assertFalse(CostoMedioCalculator.cambio(100.0, 100.0, 1L, 100.0, 100.0, 1L));
    }

    @Test
    void cambio_dentroDeTolerancia_devuelveFalse() {
        // diferencia menor a 0.01 → se considera igual (ruido de punto flotante)
        assertFalse(CostoMedioCalculator.cambio(100.0, 100.0, 1L, 100.005, 100.0, 1L));
    }

    @Test
    void cambio_costoMedioDistinto_devuelveTrue() {
        assertTrue(CostoMedioCalculator.cambio(100.0, 100.0, 1L, 113.33, 100.0, 1L));
    }

    @Test
    void cambio_ultimoPrecioDistinto_devuelveTrue() {
        // costo medio igual (mucho stock) pero el último precio cambió → hay que registrar
        assertTrue(CostoMedioCalculator.cambio(100.0, 100.0, 1L, 100.0, 130.0, 1L));
    }

    @Test
    void cambio_monedaDistinta_devuelveTrue() {
        assertTrue(CostoMedioCalculator.cambio(100.0, 100.0, 1L, 100.0, 100.0, 2L));
    }
}
