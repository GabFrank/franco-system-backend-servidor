package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.Turno;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Component
public class HorasTrabajadasCalculator {

    public void calcular(Jornada jornada) {
        Marcacion entradaParaCalculo = jornada.getMarcacionEntrada();
        if (entradaParaCalculo == null || entradaParaCalculo.getFechaEntrada() == null) return;

        LocalDateTime entradaReal = entradaParaCalculo.getFechaEntrada();
        LocalDateTime salidaReal = determinarSalidaReal(jornada);

        if (salidaReal == null) return;

        long minutosDescanso = getMinutosDescanso(jornada);
        long tiempoAlmuerzoReal = getTiempoAlmuerzoReal(jornada);
        long descansoADescontar = Math.max(minutosDescanso, tiempoAlmuerzoReal);

        // Tiempo realmente presente. Se cuenta desde la marcacion real de entrada: si el
        // funcionario llega antes de su horario, esos minutos son extras igual que los que
        // se queda de mas al final del turno.
        long totalMinutos = ChronoUnit.MINUTES.between(entradaReal, salidaReal);
        if (shouldDeductBreak(entradaReal, salidaReal, totalMinutos, tiempoAlmuerzoReal, jornada)) {
            totalMinutos -= descansoADescontar;
        }
        totalMinutos = Math.max(0, totalMinutos);

        asignarResultados(jornada, totalMinutos, calcularHorasProgramadas(jornada, minutosDescanso));
    }

    /** Minutos que el horario exige, ya sin el descanso. Sin horario cargado se asume la jornada de 8 h. */
    private long calcularHorasProgramadas(Jornada jornada, long minutosDescanso) {
        if (jornada.getHoraEntradaHorario() == null || jornada.getHoraSalidaHorario() == null) {
            return 8 * 60;
        }
        LocalDateTime hEntradaHorario = getHorarioEntrada(jornada);
        LocalDateTime hSalidaHorario = getHorarioSalida(jornada, hEntradaHorario);

        long programadas = ChronoUnit.MINUTES.between(hEntradaHorario, hSalidaHorario);
        if (programadas > minutosDescanso && abarcaDescanso(hEntradaHorario, hSalidaHorario, jornada)) {
            programadas -= minutosDescanso;
        }
        return programadas;
    }

    private LocalDateTime determinarSalidaReal(Jornada jornada) {
        if (jornada.getMarcacionSalida() != null) {
            return getFechaMarcacion(jornada.getMarcacionSalida());
        } else if (jornada.getMarcacionEntradaAlmuerzo() != null) {
            return jornada.getMarcacionEntradaAlmuerzo().getFechaEntrada();
        } else if (jornada.getMarcacionSalidaAlmuerzo() != null) {
            return getFechaMarcacion(jornada.getMarcacionSalidaAlmuerzo());
        }
        return null;
    }

    private long getMinutosDescanso(Jornada jornada) {
        if (jornada.getInicioDescansoHorario() != null && jornada.getFinDescansoHorario() != null) {
            long diff = ChronoUnit.MINUTES.between(jornada.getInicioDescansoHorario(), jornada.getFinDescansoHorario());
            return diff < 0 ? diff + 1440 : diff;
        }
        return 60;
    }

    private long getTiempoAlmuerzoReal(Jornada jornada) {
        if (jornada.getMarcacionSalidaAlmuerzo() != null && jornada.getMarcacionEntradaAlmuerzo() != null) {
            LocalDateTime salida = getFechaMarcacion(jornada.getMarcacionSalidaAlmuerzo());
            LocalDateTime entrada = jornada.getMarcacionEntradaAlmuerzo().getFechaEntrada();
            if (entrada != null && salida != null && entrada.isAfter(salida)) {
                return ChronoUnit.MINUTES.between(salida, entrada);
            }
        }
        return -1;
    }

    /**
     * Si el funcionario marco su descanso, se descuenta siempre. Si no lo marco, se descuenta
     * cuando la jornada fue larga y ademas paso por la franja de descanso.
     */
    private boolean shouldDeductBreak(LocalDateTime entradaReal, LocalDateTime salidaReal,
                                      long totalMinutos, long tiempoAlmuerzoReal, Jornada jornada) {
        if (tiempoAlmuerzoReal >= 0) return true;
        return totalMinutos > (5 * 60) && abarcaDescanso(entradaReal, salidaReal, jornada);
    }

    /**
     * Si el intervalo pasa por la franja de descanso. Se decide por el intervalo realmente
     * trabajado y no por el turno cargado en la jornada: el turno de un funcionario cambia
     * y puede no ser el que trabajo ese dia, mientras que las marcaciones no mienten.
     */
    private boolean abarcaDescanso(LocalDateTime desde, LocalDateTime hasta, Jornada jornada) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) return false;

        LocalDateTime candidato = desde.toLocalDate().atTime(momentoDescanso(jornada));
        if (candidato.isBefore(desde)) candidato = candidato.plusDays(1);
        return !candidato.isAfter(hasta);
    }

    /** Punto medio de la franja de descanso del horario; sin franja cargada, el mediodia. */
    private LocalTime momentoDescanso(Jornada jornada) {
        LocalTime inicio = jornada.getInicioDescansoHorario();
        LocalTime fin = jornada.getFinDescansoHorario();
        if (inicio == null || fin == null) return LocalTime.NOON;

        long duracion = ChronoUnit.MINUTES.between(inicio, fin);
        if (duracion < 0) duracion += 1440;
        return inicio.plusMinutes(duracion / 2);
    }

    private LocalDateTime getHorarioEntrada(Jornada jornada) {
        LocalTime hora = jornada.getHoraEntradaHorario();
        if (jornada.getTurno() == Turno.MADRUGADA) {
            return jornada.getFecha().atTime(hora);
        }
        return jornada.getMarcacionEntrada().getFechaEntrada().toLocalDate().atTime(hora);
    }

    private LocalDateTime getHorarioSalida(Jornada jornada, LocalDateTime hEntradaHorario) {
        LocalTime hora = jornada.getHoraSalidaHorario();
        LocalDateTime hSalida;
        if (jornada.getTurno() == Turno.MADRUGADA) {
            hSalida = jornada.getFecha().atTime(hora);
        } else {
            hSalida = hEntradaHorario.toLocalDate().atTime(hora);
        }
        return hSalida.isBefore(hEntradaHorario) ? hSalida.plusDays(1) : hSalida;
    }

    private void asignarResultados(Jornada jornada, long totalMinutos, long horasProgramadas) {
        if (totalMinutos > horasProgramadas) {
            jornada.setMinutosTrabajados(horasProgramadas);
            jornada.setMinutosExtras(totalMinutos - horasProgramadas);
        } else {
            jornada.setMinutosTrabajados(totalMinutos);
            jornada.setMinutosExtras(0L);
        }
    }

    private LocalDateTime getFechaMarcacion(Marcacion m) {
        return m.getFechaSalida() != null ? m.getFechaSalida() : m.getFechaEntrada();
    }
}
