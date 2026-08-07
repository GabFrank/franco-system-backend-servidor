package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AguinaldoCalculatorTest {

    @Test
    void ingresoAnioAnterior_doceMeses() {
        assertEquals(12, AguinaldoCalculator.mesesTrabajados(2026, LocalDate.of(2020, 3, 10)));
    }

    @Test
    void ingresoDuranteAnio_prorratea() {
        // ingreso en abril (mes 4): 12 - 4 + 1 = 9 meses
        assertEquals(9, AguinaldoCalculator.mesesTrabajados(2026, LocalDate.of(2026, 4, 1)));
    }

    @Test
    void ingresoPosteriorAlAnio_ceroMeses() {
        assertEquals(0, AguinaldoCalculator.mesesTrabajados(2026, LocalDate.of(2027, 1, 1)));
    }

    @Test
    void fechaIngresoNula_asumeAnioCompleto() {
        assertEquals(12, AguinaldoCalculator.mesesTrabajados(2026, null));
    }

    @Test
    void montoAnioCompleto_igualSueldo() {
        // sueldo 2.400.000 x 12 / 12 = 2.400.000
        BigDecimal monto = AguinaldoCalculator.calcularMonto(new BigDecimal("2400000"), 12);
        assertEquals(0, new BigDecimal("2400000.00").compareTo(monto));
    }

    @Test
    void montoProrrateado_conRedondeo() {
        // 2.400.000 x 9 / 12 = 1.800.000
        BigDecimal monto = AguinaldoCalculator.calcularMonto(new BigDecimal("2400000"), 9);
        assertEquals(0, new BigDecimal("1800000").compareTo(monto));
    }

    @Test
    void montoConDivisionInexacta_redondeaHalfUp() {
        // 1.000.000 x 1 / 12 = 83.333,333... -> 83333.33
        BigDecimal monto = AguinaldoCalculator.calcularMonto(new BigDecimal("1000000"), 1);
        assertEquals(0, new BigDecimal("83333.33").compareTo(monto));
    }

    @Test
    void sueldoNulo_montoCero() {
        BigDecimal monto = AguinaldoCalculator.calcularMonto(null, 12);
        assertEquals(0, BigDecimal.ZERO.compareTo(monto));
    }
}
