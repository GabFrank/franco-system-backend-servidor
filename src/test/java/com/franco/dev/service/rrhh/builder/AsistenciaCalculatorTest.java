package com.franco.dev.service.rrhh.builder;

import com.franco.dev.service.rrhh.builder.AsistenciaCalculator.DiaLaborable;
import com.franco.dev.service.rrhh.dto.AsistenciaResumenDto;
import com.franco.dev.service.rrhh.dto.EstadoAsistenciaDia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AsistenciaCalculatorTest {

    private static final long TOLERANCIA = 5;
    private static final BigDecimal PESO_FALTA = new BigDecimal("3");
    private static final BigDecimal PESO_TARDANZA = BigDecimal.ONE;

    private static DiaLaborable trabajado(int dia, long tardeEntrada, long tardeAlmuerzo) {
        return new DiaLaborable(LocalDate.of(2026, 9, dia), true, tardeEntrada, tardeAlmuerzo, false, null);
    }

    private static DiaLaborable ausente(int dia, boolean justificado) {
        return new DiaLaborable(LocalDate.of(2026, 9, dia), false, 0, 0, justificado,
                justificado ? "REPOSO MEDICO" : null);
    }

    /** {@code total} dias: los primeros son los dados y el resto se completa con dias a tiempo. */
    private static List<DiaLaborable> mes(int total, DiaLaborable... primeros) {
        List<DiaLaborable> dias = new ArrayList<>();
        for (DiaLaborable d : primeros) dias.add(d);
        for (int i = dias.size() + 1; i <= total; i++) dias.add(trabajado(i, 0, 0));
        return dias;
    }

    private static AsistenciaResumenDto calcular(List<DiaLaborable> dias) {
        return AsistenciaCalculator.calcular(2026, 9, true, dias, TOLERANCIA, PESO_FALTA, PESO_TARDANZA);
    }

    @Test
    void mesPerfecto_puntajeDiez() {
        AsistenciaResumenDto r = calcular(mes(24));
        assertEquals(24, r.getDiasEsperados());
        assertEquals(24, r.getDiasTrabajados());
        assertEquals(0, r.getLlegadasTardias());
        assertEquals(100, r.getPuntualidad());
        assertEquals(10.0, r.getPuntaje());
    }

    @Test
    void dosTardanzasDeEntrada_restanUnPesoCadaUna() {
        AsistenciaResumenDto r = calcular(mes(24, trabajado(1, 10, 0), trabajado(2, 30, 0)));
        assertEquals(2, r.getLlegadasTardias());
        assertEquals(24, r.getDiasTrabajados());
        assertEquals(92, r.getPuntualidad()); // 22 a tiempo de 24
        assertEquals(9.2, r.getPuntaje());    // 10 x (1 - 2/24) = 9,17
        assertEquals(EstadoAsistenciaDia.TARDANZA, r.getDias().get(0).getEstado());
    }

    @Test
    void tardanzaDeAlmuerzo_tambienResta() {
        AsistenciaResumenDto r = calcular(mes(24, trabajado(1, 0, 15)));
        assertEquals(1, r.getLlegadasTardias());
        assertEquals(EstadoAsistenciaDia.TARDANZA, r.getDias().get(0).getEstado());
        assertEquals(9.6, r.getPuntaje()); // 10 x (1 - 1/24) = 9,58
    }

    @Test
    void entradaYAlmuerzoElMismoDia_sonDosEventosYUnSoloDiaTarde() {
        AsistenciaResumenDto r = calcular(mes(24, trabajado(1, 10, 15)));
        assertEquals(2, r.getLlegadasTardias());
        assertEquals(96, r.getPuntualidad()); // 23 a tiempo de 24
        assertEquals(9.2, r.getPuntaje());
    }

    @Test
    void tardanzaIgualALaTolerancia_noCuenta() {
        AsistenciaResumenDto r = calcular(mes(24, trabajado(1, 5, 5), trabajado(2, 6, 0)));
        assertEquals(1, r.getLlegadasTardias());
        assertEquals(EstadoAsistenciaDia.TRABAJADO, r.getDias().get(0).getEstado());
        assertEquals(EstadoAsistenciaDia.TARDANZA, r.getDias().get(1).getEstado());
    }

    @Test
    void tardanzaJustificada_noCuenta() {
        DiaLaborable d = new DiaLaborable(LocalDate.of(2026, 9, 1), true, 40, 20, true, "JUSTIFICADO");
        AsistenciaResumenDto r = calcular(mes(24, d));
        assertEquals(0, r.getLlegadasTardias());
        assertEquals(EstadoAsistenciaDia.TRABAJADO, r.getDias().get(0).getEstado());
        assertEquals("JUSTIFICADO", r.getDias().get(0).getJustificativo());
        assertEquals(10.0, r.getPuntaje());
    }

    @Test
    void ausenciaJustificada_noRestaPeroNoEsDiaTrabajado() {
        AsistenciaResumenDto r = calcular(mes(24, ausente(1, true)));
        assertEquals(1, r.getAusenciasJustificadas());
        assertEquals(0, r.getAusenciasInjustificadas());
        assertEquals(23, r.getDiasTrabajados());
        assertEquals(24, r.getDiasEsperados());
        assertEquals(10.0, r.getPuntaje());
        assertEquals(EstadoAsistenciaDia.AUSENCIA_JUSTIFICADA, r.getDias().get(0).getEstado());
    }

    @Test
    void ausenciaInjustificada_restaElPesoDeFalta() {
        AsistenciaResumenDto r = calcular(mes(24, ausente(1, false)));
        assertEquals(1, r.getAusenciasInjustificadas());
        assertEquals(8.8, r.getPuntaje()); // 10 x (1 - 3/24) = 8,75
        assertEquals(EstadoAsistenciaDia.AUSENCIA_INJUSTIFICADA, r.getDias().get(0).getEstado());
    }

    @Test
    void muchasFaltas_elPuntajeNoBajaDeCero() {
        List<DiaLaborable> dias = new ArrayList<>();
        for (int i = 1; i <= 24; i++) dias.add(ausente(i, false));
        AsistenciaResumenDto r = calcular(dias);
        assertEquals(0.0, r.getPuntaje());
        assertEquals(0, r.getDiasTrabajados());
        assertNull(r.getPuntualidad());
    }

    @Test
    void sinDiasEsperados_noHayPuntaje() {
        AsistenciaResumenDto r = calcular(new ArrayList<>());
        assertEquals(0, r.getDiasEsperados());
        assertNull(r.getPuntaje());
        assertNull(r.getPuntualidad());
    }

    @Test
    void pesosNulos_usanLosDefault() {
        AsistenciaResumenDto r = AsistenciaCalculator.calcular(2026, 9, true, mes(24, ausente(1, false)),
                TOLERANCIA, null, null);
        assertEquals(8.8, r.getPuntaje());
    }

    // --- franco semanal ---------------------------------------------------
    // Septiembre 2026: el 7 es lunes y el 13 domingo.

    private static List<DiaLaborable> semana(int lunes, Integer... ausentes) {
        List<Integer> faltas = java.util.Arrays.asList(ausentes);
        List<DiaLaborable> dias = new ArrayList<>();
        for (int d = lunes; d < lunes + 7; d++) dias.add(faltas.contains(d) ? ausente(d, false) : trabajado(d, 0, 0));
        return dias;
    }

    @Test
    void franco_unDiaSinMarcarPorSemanaNoEsFalta() {
        List<DiaLaborable> dias = AsistenciaCalculator.sinFrancoSemanal(semana(7, 10));
        assertEquals(6, dias.size());
        assertEquals(10.0, calcular(dias).getPuntaje());
    }

    @Test
    void franco_elSegundoDiaSinMarcarSiEsFalta() {
        List<DiaLaborable> dias = AsistenciaCalculator.sinFrancoSemanal(semana(7, 9, 13));
        assertEquals(6, dias.size());
        AsistenciaResumenDto r = calcular(dias);
        assertEquals(1, r.getAusenciasInjustificadas());
        assertEquals(LocalDate.of(2026, 9, 13).toString(), r.getDias().get(5).getFecha());
    }

    @Test
    void franco_quienTrabajaLosSieteDiasConservaLosSiete() {
        assertEquals(7, AsistenciaCalculator.sinFrancoSemanal(semana(7)).size());
    }

    @Test
    void franco_unaAusenciaJustificadaNoGastaElFranco() {
        List<DiaLaborable> dias = semana(7, 13);
        dias.set(1, ausente(8, true));
        List<DiaLaborable> r = AsistenciaCalculator.sinFrancoSemanal(dias);
        assertEquals(6, r.size());
        assertEquals(0, calcular(r).getAusenciasInjustificadas());
        assertEquals(1, calcular(r).getAusenciasJustificadas());
    }

    @Test
    void franco_cadaSemanaTieneElSuyo() {
        List<DiaLaborable> dias = semana(7, 8);
        dias.addAll(semana(14, 20));
        assertEquals(12, AsistenciaCalculator.sinFrancoSemanal(dias).size());
    }

    // --- rango evaluado -------------------------------------------------

    @Test
    void rango_mesPasadoCompleto() {
        LocalDate[] r = AsistenciaCalculator.rango(2026, 9, null, null, LocalDate.of(2026, 10, 12));
        assertEquals(LocalDate.of(2026, 9, 1), r[0]);
        assertEquals(LocalDate.of(2026, 9, 30), r[1]);
    }

    @Test
    void rango_mesEnCursoLlegaHastaHoy() {
        LocalDate[] r = AsistenciaCalculator.rango(2026, 10, null, null, LocalDate.of(2026, 10, 12));
        assertEquals(LocalDate.of(2026, 10, 1), r[0]);
        assertEquals(LocalDate.of(2026, 10, 12), r[1]);
    }

    @Test
    void rango_arrancaEnElIngresoYCortaEnElEgreso() {
        LocalDate[] r = AsistenciaCalculator.rango(2026, 9, LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 12));
        assertEquals(LocalDate.of(2026, 9, 10), r[0]);
        assertEquals(LocalDate.of(2026, 9, 20), r[1]);
    }

    @Test
    void rango_mesFuturoOAnteriorAlIngreso_esVacio() {
        assertNull(AsistenciaCalculator.rango(2026, 11, null, null, LocalDate.of(2026, 10, 12)));
        assertNull(AsistenciaCalculator.rango(2026, 8, LocalDate.of(2026, 9, 10), null, LocalDate.of(2026, 10, 12)));
    }
}
