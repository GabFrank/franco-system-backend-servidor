package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VacacionDevengamientoCalculatorTest {

    // valores por defecto de ley PY: 12 / 18 / 30
    @Test
    void menosDe5Anios_doce() {
        assertEquals(12, VacacionDevengamientoCalculator.diasPorAntiguedad(3, 12, 18, 30));
        assertEquals(12, VacacionDevengamientoCalculator.diasPorAntiguedad(0, 12, 18, 30));
        assertEquals(12, VacacionDevengamientoCalculator.diasPorAntiguedad(4, 12, 18, 30));
    }

    @Test
    void entre5y10Anios_dieciocho() {
        assertEquals(18, VacacionDevengamientoCalculator.diasPorAntiguedad(5, 12, 18, 30));
        assertEquals(18, VacacionDevengamientoCalculator.diasPorAntiguedad(10, 12, 18, 30));
    }

    @Test
    void masDe10Anios_treinta() {
        assertEquals(30, VacacionDevengamientoCalculator.diasPorAntiguedad(11, 12, 18, 30));
        assertEquals(30, VacacionDevengamientoCalculator.diasPorAntiguedad(25, 12, 18, 30));
    }

    @Test
    void respetaValoresParametrizados() {
        assertEquals(15, VacacionDevengamientoCalculator.diasPorAntiguedad(2, 15, 20, 35));
    }
}
