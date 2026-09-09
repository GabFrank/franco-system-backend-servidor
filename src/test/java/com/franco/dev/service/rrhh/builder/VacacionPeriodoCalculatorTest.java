package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacacionPeriodoCalculatorTest {

    /**
     * El caso que motivo el fix: al asignar 12 dias de vacaciones el rango de fechas
     * abarca mas dias corridos que dias de saldo, porque los domingos son dia libre y
     * no se descuentan. Antes se contaban corridos y la solicitud se rechazaba con
     * "Los dias del periodo superan los dias disponibles: 12".
     */
    @Test
    void doceDiasHabilesAbarcanMasDiasCorridos() {
        // lunes 07/09 -> sabado 19/09: 13 dias corridos, 1 domingo en el medio
        assertEquals(12, VacacionPeriodoCalculator.diasLaborables(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 19)));
        // martes 08/09 -> lunes 21/09: 14 dias corridos, 2 domingos en el medio
        assertEquals(12, VacacionPeriodoCalculator.diasLaborables(
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 21)));
    }

    @Test
    void unaSemanaCompletaSonSeisDias() {
        LocalDate lunes = LocalDate.of(2026, 9, 7);
        assertEquals(6, VacacionPeriodoCalculator.diasLaborables(lunes, lunes.plusDays(6)));
    }

    @Test
    void rangoDeUnSoloDiaLaborable() {
        LocalDate martes = LocalDate.of(2026, 9, 8);
        assertEquals(1, VacacionPeriodoCalculator.diasLaborables(martes, martes));
    }

    @Test
    void rangoDeUnSoloDomingoNoDescuentaNada() {
        LocalDate domingo = LocalDate.of(2026, 9, 13);
        assertEquals(0, VacacionPeriodoCalculator.diasLaborables(domingo, domingo));
    }

    @Test
    void rangoQueEmpiezaYTerminaEnDomingo() {
        LocalDate domingo = LocalDate.of(2026, 9, 13);
        // domingo a domingo: 8 dias corridos, 6 laborables (los dos domingos no cuentan)
        assertEquals(6, VacacionPeriodoCalculator.diasLaborables(domingo, domingo.plusDays(7)));
    }

    @Test
    void rangoInvertidoOIncompletoDaCero() {
        LocalDate lunes = LocalDate.of(2026, 9, 7);
        assertEquals(0, VacacionPeriodoCalculator.diasLaborables(lunes, lunes.minusDays(1)));
        assertEquals(0, VacacionPeriodoCalculator.diasLaborables(null, lunes));
        assertEquals(0, VacacionPeriodoCalculator.diasLaborables(lunes, null));
    }

    @Test
    void esDiaLaborableDistingueElDomingo() {
        assertFalse(VacacionPeriodoCalculator.esDiaLaborable(LocalDate.of(2026, 9, 13)));
        assertTrue(VacacionPeriodoCalculator.esDiaLaborable(LocalDate.of(2026, 9, 12)));
        assertTrue(VacacionPeriodoCalculator.esDiaLaborable(LocalDate.of(2026, 9, 14)));
        assertFalse(VacacionPeriodoCalculator.esDiaLaborable(null));
    }
}
