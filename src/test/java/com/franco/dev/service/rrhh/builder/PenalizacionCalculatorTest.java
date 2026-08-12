package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PenalizacionCalculatorTest {

    @Test
    void fijoMasPorMinuto() {
        // 50.000 fijo + 1.000 x 15 min = 65.000
        BigDecimal monto = PenalizacionCalculator.calcularMontoTardanza(
                new BigDecimal("50000"), new BigDecimal("1000"), 15);
        assertEquals(0, new BigDecimal("65000").compareTo(monto));
    }

    @Test
    void soloFijo_cuandoPorMinutoCero() {
        BigDecimal monto = PenalizacionCalculator.calcularMontoTardanza(
                new BigDecimal("30000"), BigDecimal.ZERO, 40);
        assertEquals(0, new BigDecimal("30000").compareTo(monto));
    }

    @Test
    void soloPorMinuto_cuandoFijoCero() {
        BigDecimal monto = PenalizacionCalculator.calcularMontoTardanza(
                BigDecimal.ZERO, new BigDecimal("500"), 20);
        assertEquals(0, new BigDecimal("10000").compareTo(monto));
    }

    @Test
    void montosNulos_seTratanComoCero() {
        BigDecimal monto = PenalizacionCalculator.calcularMontoTardanza(null, null, 10);
        assertEquals(0, BigDecimal.ZERO.compareTo(monto));
    }

    @Test
    void minutosNegativos_seClampeanACero() {
        BigDecimal monto = PenalizacionCalculator.calcularMontoTardanza(
                new BigDecimal("50000"), new BigDecimal("1000"), -5);
        assertEquals(0, new BigDecimal("50000").compareTo(monto));
    }
}
