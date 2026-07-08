package com.franco.dev.service.rrhh.builder;

import com.franco.dev.domain.rrhh.enums.MotivoEgreso;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidacionFinalCalculatorTest {

    private static final BigDecimal DIAS_POR_ANIO = new BigDecimal("15");

    @Test
    void antiguedad_diasMesesAnios() {
        LiquidacionFinalCalculator.Antiguedad a =
                LiquidacionFinalCalculator.antiguedad(LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1));
        assertEquals(1096, a.getDias()); // 3 años (incluye un bisiesto)
        assertEquals(3, a.getAnios());
        assertEquals(36, a.getMeses());
    }

    @Test
    void egresoAntesDeIngreso_antiguedadCero() {
        LiquidacionFinalCalculator.Antiguedad a =
                LiquidacionFinalCalculator.antiguedad(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1));
        assertEquals(0, a.getDias());
        assertEquals(0, a.getAnios());
    }

    @Test
    void despidoInjustificado_calculaIndemnizacion() {
        // ingreso 2023-01-01, egreso 2026-01-01 => 3 años, 1096 días (>=90)
        // indemnizacion = 3.000.000/30 × 15 × 3 = 100.000 × 45 = 4.500.000
        // vac: 10 días × 3.000.000/30 = 10 × 100.000 = 1.000.000
        // aguinaldo: 250.000
        // total = 5.750.000
        LiquidacionFinalCalculator.Resultado r = LiquidacionFinalCalculator.calcular(
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1),
                MotivoEgreso.DESPIDO_INJUSTIFICADO,
                new BigDecimal("3000000"), 10, new BigDecimal("250000"), DIAS_POR_ANIO);
        assertTrue(r.isIndemnizacionAplica());
        assertEquals(0, new BigDecimal("4500000.00").compareTo(r.getIndemnizacionMonto()));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(r.getMontoVacacionesNoGozadas()));
        assertEquals(0, new BigDecimal("250000").compareTo(r.getAguinaldoProporcional()));
        assertEquals(0, new BigDecimal("5750000.00").compareTo(r.getTotalLiquidado()));
    }

    @Test
    void renuncia_noPagaIndemnizacion_peroSiVacacionesYAguinaldo() {
        LiquidacionFinalCalculator.Resultado r = LiquidacionFinalCalculator.calcular(
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1),
                MotivoEgreso.RENUNCIA,
                new BigDecimal("3000000"), 10, new BigDecimal("250000"), DIAS_POR_ANIO);
        assertFalse(r.isIndemnizacionAplica());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getIndemnizacionMonto()));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(r.getMontoVacacionesNoGozadas()));
        assertEquals(0, new BigDecimal("1250000.00").compareTo(r.getTotalLiquidado()));
    }

    @Test
    void despidoInjustificadoMenorA90Dias_noIndemniza() {
        // ingreso 2025-11-01, egreso 2026-01-01 => 61 días (<90)
        LiquidacionFinalCalculator.Resultado r = LiquidacionFinalCalculator.calcular(
                LocalDate.of(2025, 11, 1), LocalDate.of(2026, 1, 1),
                MotivoEgreso.DESPIDO_INJUSTIFICADO,
                new BigDecimal("3000000"), 0, BigDecimal.ZERO, DIAS_POR_ANIO);
        assertFalse(r.isIndemnizacionAplica());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getIndemnizacionMonto()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getTotalLiquidado()));
    }

    @Test
    void antiguedadMenorAUnAnio_usaMinimoUnAnio() {
        // 120 días (>=90) pero <1 año => años=0 -> max(1,0)=1
        // indemnizacion = 3.000.000/30 × 15 × 1 = 1.500.000
        LiquidacionFinalCalculator.Resultado r = LiquidacionFinalCalculator.calcular(
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 12, 30),
                MotivoEgreso.DESPIDO_INJUSTIFICADO,
                new BigDecimal("3000000"), 0, BigDecimal.ZERO, DIAS_POR_ANIO);
        assertTrue(r.isIndemnizacionAplica());
        assertEquals(0, new BigDecimal("1500000.00").compareTo(r.getIndemnizacionMonto()));
    }
}
