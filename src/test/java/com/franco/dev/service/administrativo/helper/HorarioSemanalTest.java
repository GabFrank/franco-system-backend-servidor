package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.enums.Dia;
import com.franco.dev.domain.administrativo.enums.Turno;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HorarioSemanalTest {

    private Horario horario(Long id, int entrada, int salida, Turno turno, Dia... dias) {
        Horario h = new Horario();
        h.setId(id);
        h.setHoraEntrada(LocalTime.of(entrada, 0));
        h.setHoraSalida(LocalTime.of(salida, 0));
        h.setTurno(turno);
        h.setDias(new HashSet<>(Arrays.asList(dias)));
        return h;
    }

    @Test
    void conDosTurnosElMismoDia_ganaElDeEntradaMasCercana() {
        Horario manana = horario(1L, 7, 12, Turno.DIA, Dia.MIERCOLES);
        Horario noche = horario(2L, 18, 23, Turno.NOCHE, Dia.MIERCOLES);
        List<Horario> lista = Arrays.asList(noche, manana);

        assertEquals(manana, HorarioSemanal.elegir(lista, Dia.MIERCOLES, LocalTime.of(6, 50)));
        assertEquals(noche, HorarioSemanal.elegir(lista, Dia.MIERCOLES, LocalTime.of(17, 40)));
    }

    @Test
    void laDistanciaDaLaVueltaAMedianoche() {
        Horario tarde = horario(1L, 14, 22, Turno.DIA, Dia.VIERNES);
        Horario madrugada = horario(2L, 0, 6, Turno.MADRUGADA, Dia.VIERNES);

        assertEquals(madrugada, HorarioSemanal.elegir(Arrays.asList(tarde, madrugada), Dia.VIERNES, LocalTime.of(23, 50)));
    }

    @Test
    void losDeTodosYLosDelDia_compitenPorLaHoraDeEntrada() {
        // TODOS Dia 08:00-17:00 y jueves Madrugada 17:00-01:00: el jueves rigen los dos.
        Horario todos = horario(9L, 8, 17, Turno.DIA, Dia.TODOS);
        Horario jueves = horario(8L, 17, 1, Turno.MADRUGADA, Dia.JUEVES);
        List<Horario> lista = Arrays.asList(todos, jueves);

        assertEquals(jueves, HorarioSemanal.elegir(lista, Dia.JUEVES, LocalTime.of(16, 55)));
        assertEquals(todos, HorarioSemanal.elegir(lista, Dia.JUEVES, LocalTime.of(7, 55)));
        assertEquals(todos, HorarioSemanal.elegir(lista, Dia.LUNES, LocalTime.of(16, 55)));
    }

    @Test
    void horariosQueSePisan_sePuedenTener_yGanaElDelDiaEnElEmpate() {
        // El mismo horario cargado en TODOS y en jueves: vale, y el jueves usa el del dia.
        Horario todos = horario(10L, 1, 8, Turno.DIA, Dia.TODOS);
        Horario jueves = horario(11L, 1, 8, Turno.MADRUGADA, Dia.JUEVES);
        Horario pisado = horario(12L, 2, 4, Turno.DIA, Dia.JUEVES);

        assertEquals(jueves, HorarioSemanal.elegir(Arrays.asList(todos, jueves, pisado), Dia.JUEVES, LocalTime.of(0, 55)));
        assertEquals(pisado, HorarioSemanal.elegir(Arrays.asList(todos, jueves, pisado), Dia.JUEVES, LocalTime.of(1, 58)));
        assertEquals(todos, HorarioSemanal.elegir(Arrays.asList(todos, jueves, pisado), Dia.MARTES, LocalTime.of(0, 55)));
    }

    @Test
    void resumen_agrupaDiasSeguidosConElMismoHorario() {
        String resumen = HorarioSemanal.resumen(Arrays.asList(
                horario(1L, 8, 17, Turno.DIA, Dia.LUNES, Dia.MARTES, Dia.MIERCOLES),
                horario(2L, 17, 1, Turno.MADRUGADA, Dia.JUEVES)));

        assertEquals("L-Mi Día 08:00-17:00 · J Madrugada 17:00-01:00", resumen);
    }

    @Test
    void resumen_conDosTurnosElMismoDia() {
        String resumen = HorarioSemanal.resumen(Arrays.asList(
                horario(1L, 7, 12, Turno.DIA, Dia.TODOS),
                horario(2L, 18, 23, Turno.NOCHE, Dia.MIERCOLES),
                horario(3L, 7, 12, Turno.DIA, Dia.MIERCOLES)));

        assertEquals("L-Ma Día 07:00-12:00 · Mi Día 07:00-12:00 + Noche 18:00-23:00 · J-D Día 07:00-12:00", resumen);
    }

    @Test
    void resumen_todaLaSemanaIgual() {
        assertEquals("Todos Día 08:00-17:00",
                HorarioSemanal.resumen(Collections.singletonList(horario(6L, 8, 17, Turno.DIA, Dia.TODOS))));
    }

    @Test
    void resumen_sinHorario_esNull() {
        assertNull(HorarioSemanal.resumen(Collections.emptyList()));
    }
}
