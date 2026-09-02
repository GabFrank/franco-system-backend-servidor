package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.Turno;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HorasTrabajadasCalculatorTest {

    private static final LocalDate DIA_HABIL = LocalDate.of(2026, 9, 1);

    private final HorasTrabajadasCalculator calculator = new HorasTrabajadasCalculator();

    /**
     * Horario de dia 08:00-17:00 con una hora de descanso: 8 h programadas.
     * El descanso no se marca (el caso mas comun en produccion).
     */
    private Jornada jornadaDiurna(LocalTime entradaReal, LocalTime salidaReal) {
        Jornada jornada = new Jornada();
        jornada.setFecha(DIA_HABIL);
        jornada.setTurno(Turno.DIA);
        jornada.setHoraEntradaHorario(LocalTime.of(8, 0));
        jornada.setHoraSalidaHorario(LocalTime.of(17, 0));
        jornada.setMarcacionEntrada(entrada(DIA_HABIL.atTime(entradaReal)));
        jornada.setMarcacionSalida(salida(DIA_HABIL.atTime(salidaReal)));
        return jornada;
    }

    private Marcacion entrada(LocalDateTime fechaEntrada) {
        Marcacion m = new Marcacion();
        m.setFechaEntrada(fechaEntrada);
        return m;
    }

    private Marcacion salida(LocalDateTime fechaSalida) {
        Marcacion m = new Marcacion();
        m.setFechaSalida(fechaSalida);
        return m;
    }

    @Test
    void entradaAnticipada_cuentaComoExtra() {
        // Entra 07:51 (9 min antes del horario) y sale 17:00 en punto.
        Jornada jornada = jornadaDiurna(LocalTime.of(7, 51), LocalTime.of(17, 0));

        calculator.calcular(jornada);

        assertEquals(9L, jornada.getMinutosExtras());
        assertEquals(480L, jornada.getMinutosTrabajados());
    }

    @Test
    void entradaMuyAnticipada_cuentaCompletaComoExtra() {
        // Una hora antes: no hay tope, se cuentan los 60 min.
        Jornada jornada = jornadaDiurna(LocalTime.of(7, 0), LocalTime.of(17, 0));

        calculator.calcular(jornada);

        assertEquals(60L, jornada.getMinutosExtras());
    }

    @Test
    void entradaYSalidaEnHorario_noGeneraExtras() {
        Jornada jornada = jornadaDiurna(LocalTime.of(8, 0), LocalTime.of(17, 0));

        calculator.calcular(jornada);

        assertEquals(0L, jornada.getMinutosExtras());
        assertEquals(480L, jornada.getMinutosTrabajados());
    }

    @Test
    void salidaTardia_cuentaComoExtra() {
        Jornada jornada = jornadaDiurna(LocalTime.of(8, 0), LocalTime.of(17, 30));

        calculator.calcular(jornada);

        assertEquals(30L, jornada.getMinutosExtras());
    }

    @Test
    void entradaAnticipadaYSalidaTardia_sumanAmbas() {
        Jornada jornada = jornadaDiurna(LocalTime.of(7, 45), LocalTime.of(17, 20));

        calculator.calcular(jornada);

        assertEquals(35L, jornada.getMinutosExtras());
    }

    @Test
    void llegadaTardia_noGeneraExtrasYRestaTrabajados() {
        Jornada jornada = jornadaDiurna(LocalTime.of(8, 10), LocalTime.of(17, 0));

        calculator.calcular(jornada);

        assertEquals(0L, jornada.getMinutosExtras());
        assertEquals(470L, jornada.getMinutosTrabajados());
    }

    @Test
    void sinHorarioAsignado_usaJornadaDe8HorasYDescuentaDescanso() {
        // Rama de respaldo: sin horario en la jornada, 8 h programadas.
        Jornada jornada = new Jornada();
        jornada.setFecha(DIA_HABIL);
        jornada.setMarcacionEntrada(entrada(DIA_HABIL.atTime(7, 51)));
        jornada.setMarcacionSalida(salida(DIA_HABIL.atTime(17, 0)));

        calculator.calcular(jornada);

        // 549 min de presencia - 60 de descanso = 489 -> 480 trabajados + 9 extras
        assertEquals(9L, jornada.getMinutosExtras());
        assertEquals(480L, jornada.getMinutosTrabajados());
    }

    @Test
    void turnoNocturnoQueCruzaMedianoche_noDescuentaDescanso() {
        Jornada jornada = new Jornada();
        jornada.setFecha(DIA_HABIL);
        jornada.setTurno(Turno.MADRUGADA);
        jornada.setHoraEntradaHorario(LocalTime.of(17, 0));
        jornada.setHoraSalidaHorario(LocalTime.of(1, 0));
        jornada.setMarcacionEntrada(entrada(DIA_HABIL.atTime(17, 0)));
        jornada.setMarcacionSalida(salida(DIA_HABIL.plusDays(1).atTime(1, 30)));

        calculator.calcular(jornada);

        assertEquals(30L, jornada.getMinutosExtras());
        assertEquals(480L, jornada.getMinutosTrabajados());
    }

    /**
     * Jornada 525 de bodega3: PAULINHO tiene cargado el turno MADRUGADA 17:00-01:00 pero
     * ese dia marco de manana. El descanso se decide por lo que trabajo, no por el turno
     * que dice la configuracion, que puede cambiar de un dia para el otro.
     */
    @Test
    void turnoNocturnoConfiguradoPeroTrabajoDeDia_descuentaElAlmuerzo() {
        Jornada jornada = new Jornada();
        jornada.setFecha(DIA_HABIL);
        jornada.setTurno(Turno.MADRUGADA);
        jornada.setHoraEntradaHorario(LocalTime.of(17, 0));
        jornada.setHoraSalidaHorario(LocalTime.of(1, 0));
        jornada.setMarcacionEntrada(entrada(DIA_HABIL.atTime(7, 49)));
        jornada.setMarcacionSalida(salida(DIA_HABIL.atTime(16, 2)));

        calculator.calcular(jornada);

        // 493 min de presencia - 60 de almuerzo = 433, por debajo de los 480 programados.
        assertEquals(433L, jornada.getMinutosTrabajados());
        assertEquals(0L, jornada.getMinutosExtras());
    }

    @Test
    void turnoDiurnoConfiguradoPeroTrabajoDeNoche_noDescuentaAlmuerzo() {
        Jornada jornada = new Jornada();
        jornada.setFecha(DIA_HABIL);
        jornada.setTurno(Turno.DIA);
        jornada.setHoraEntradaHorario(LocalTime.of(8, 0));
        jornada.setHoraSalidaHorario(LocalTime.of(17, 0));
        jornada.setMarcacionEntrada(entrada(DIA_HABIL.atTime(17, 0)));
        jornada.setMarcacionSalida(salida(DIA_HABIL.plusDays(1).atTime(1, 0)));

        calculator.calcular(jornada);

        // Trabajo de noche: no paso por el mediodia, no hay almuerzo que descontar.
        assertEquals(480L, jornada.getMinutosTrabajados());
        assertEquals(0L, jornada.getMinutosExtras());
    }

    @Test
    void almuerzoMarcado_seDescuentaAunqueElTurnoSeaNocturno() {
        Jornada jornada = new Jornada();
        jornada.setFecha(DIA_HABIL);
        jornada.setTurno(Turno.MADRUGADA);
        jornada.setHoraEntradaHorario(LocalTime.of(17, 0));
        jornada.setHoraSalidaHorario(LocalTime.of(1, 0));
        jornada.setMarcacionEntrada(entrada(DIA_HABIL.atTime(17, 0)));
        jornada.setMarcacionSalidaAlmuerzo(salida(DIA_HABIL.atTime(21, 0)));
        jornada.setMarcacionEntradaAlmuerzo(entrada(DIA_HABIL.atTime(21, 30)));
        jornada.setMarcacionSalida(salida(DIA_HABIL.plusDays(1).atTime(1, 30)));

        calculator.calcular(jornada);

        // Si el funcionario marco su descanso, se descuenta el tiempo real aunque el
        // turno nocturno no pase por el mediodia.
        assertEquals(450L, jornada.getMinutosTrabajados());
        assertEquals(0L, jornada.getMinutosExtras());
    }

    @Test
    void franjaDeDescansoDelHorario_mandaSobreElMediodia() {
        // Horario de tarde 14:00-23:00 con descanso 18:00-19:00: no pasa por el mediodia
        // pero igual tiene descanso, y hay que descontarlo.
        Jornada jornada = new Jornada();
        jornada.setFecha(DIA_HABIL);
        jornada.setTurno(Turno.DIA);
        jornada.setHoraEntradaHorario(LocalTime.of(14, 0));
        jornada.setHoraSalidaHorario(LocalTime.of(23, 0));
        jornada.setInicioDescansoHorario(LocalTime.of(18, 0));
        jornada.setFinDescansoHorario(LocalTime.of(19, 0));
        jornada.setMarcacionEntrada(entrada(DIA_HABIL.atTime(14, 0)));
        jornada.setMarcacionSalida(salida(DIA_HABIL.atTime(23, 0)));

        calculator.calcular(jornada);

        assertEquals(480L, jornada.getMinutosTrabajados());
        assertEquals(0L, jornada.getMinutosExtras());
    }

    @Test
    void almuerzoMasLargoQueElDescanso_descuentaElTiempoReal() {
        Jornada jornada = jornadaDiurna(LocalTime.of(8, 0), LocalTime.of(17, 0));
        jornada.setMarcacionSalidaAlmuerzo(salida(DIA_HABIL.atTime(12, 0)));
        jornada.setMarcacionEntradaAlmuerzo(entrada(DIA_HABIL.atTime(13, 30)));

        calculator.calcular(jornada);

        // 540 de presencia - 90 de almuerzo real = 450 < 480 programados
        assertEquals(0L, jornada.getMinutosExtras());
        assertEquals(450L, jornada.getMinutosTrabajados());
    }
}
