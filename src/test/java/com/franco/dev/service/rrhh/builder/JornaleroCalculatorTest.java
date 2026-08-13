package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JornaleroCalculatorTest {

    @Test
    void jornalPorDias() {
        // 80.000 × 22 días = 1.760.000
        BigDecimal base = JornaleroCalculator.calcularSalarioBase(new BigDecimal("80000"), 22);
        assertEquals(0, new BigDecimal("1760000.00").compareTo(base));
    }

    @Test
    void ceroDias_baseCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(JornaleroCalculator.calcularSalarioBase(new BigDecimal("80000"), 0)));
    }

    @Test
    void diasNegativos_seClampeanACero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(JornaleroCalculator.calcularSalarioBase(new BigDecimal("80000"), -3)));
    }

    @Test
    void valorJornalNulo_baseCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(JornaleroCalculator.calcularSalarioBase(null, 22)));
    }
}
