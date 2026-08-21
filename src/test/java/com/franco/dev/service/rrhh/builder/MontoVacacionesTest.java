package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El monto de las vacaciones no gozadas se calcula en dos lugares: al generar el
 * finiquito y en el preview del dialogo, que no llama a {@code calcular()}. Este test
 * fija la formula para que no se separen.
 */
public class MontoVacacionesTest {

    @Test
    void esElPromedioDiarioPorLosDias() {
        // 3.000.000 / 30 = 100.000 por dia; 12 dias = 1.200.000
        assertEquals(0, new BigDecimal("1200000").compareTo(
                LiquidacionFinalCalculator.montoVacaciones(new BigDecimal("3000000"), 12, 30)));
    }

    @Test
    void sinDiasNoHayMonto() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                LiquidacionFinalCalculator.montoVacaciones(new BigDecimal("3000000"), 0, 30)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                LiquidacionFinalCalculator.montoVacaciones(new BigDecimal("3000000"), -5, 30)));
    }

    @Test
    void sinPromedioNoRompe() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                LiquidacionFinalCalculator.montoVacaciones(null, 12, 30)));
    }

    /** Un divisor invalido cae a 30, que es la convencion del modulo (DIAS_MES_PROMEDIO). */
    @Test
    void unDivisorInvalidoCaeATreinta() {
        assertEquals(0, LiquidacionFinalCalculator.montoVacaciones(new BigDecimal("3000000"), 12, 30)
                .compareTo(LiquidacionFinalCalculator.montoVacaciones(new BigDecimal("3000000"), 12, 0)));
    }
}
