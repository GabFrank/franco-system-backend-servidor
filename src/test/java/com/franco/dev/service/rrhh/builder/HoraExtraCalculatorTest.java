package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HoraExtraCalculatorTest {

    // sueldo 3.000.000 / 30 / 8 = 12.500 por hora
    private static final BigDecimal SUELDO = new BigDecimal("3000000");
    private static final BigDecimal JORNADA = new BigDecimal("8");

    @Test
    void conRecargo50_dosHoras() {
        // 12.500 × 2h × 1.5 = 37.500
        BigDecimal monto = HoraExtraCalculator.calcularMonto(SUELDO, JORNADA, new BigDecimal("120"), new BigDecimal("50"));
        assertEquals(0, new BigDecimal("37500.00").compareTo(monto));
    }

    @Test
    void sinRecargo_dosHoras() {
        // 12.500 × 2h × 1.0 = 25.000
        BigDecimal monto = HoraExtraCalculator.calcularMonto(SUELDO, JORNADA, new BigDecimal("120"), BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("25000.00").compareTo(monto));
    }

    @Test
    void recargoNulo_seTrataComoCero() {
        BigDecimal monto = HoraExtraCalculator.calcularMonto(SUELDO, JORNADA, new BigDecimal("60"), null);
        assertEquals(0, new BigDecimal("12500.00").compareTo(monto));
    }

    @Test
    void sueldoCero_montoCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                HoraExtraCalculator.calcularMonto(BigDecimal.ZERO, JORNADA, new BigDecimal("120"), new BigDecimal("50"))));
    }

    @Test
    void minutosCero_montoCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                HoraExtraCalculator.calcularMonto(SUELDO, JORNADA, BigDecimal.ZERO, new BigDecimal("50"))));
    }

    @Test
    void horasJornadaCero_montoCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                HoraExtraCalculator.calcularMonto(SUELDO, BigDecimal.ZERO, new BigDecimal("120"), new BigDecimal("50"))));
    }
}
