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

        long totalMinutos;
        long horasProgramadas;

        if (jornada.getHoraEntradaHorario() != null && jornada.getHoraSalidaHorario() != null) {
            LocalDateTime hEntradaHorario = getHorarioEntrada(jornada);
            LocalDateTime hSalidaHorario = getHorarioSalida(jornada, hEntradaHorario);

            LocalDateTime entradaCalculo = ajustarEntrada(entradaReal, hEntradaHorario);
            totalMinutos = ChronoUnit.MINUTES.between(entradaCalculo, salidaReal);
            
            if (shouldDeductBreak(totalMinutos, tiempoAlmuerzoReal, jornada.getTurno())) {
                totalMinutos -= descansoADescontar;
            }
            totalMinutos = Math.max(0, totalMinutos);

            horasProgramadas = ChronoUnit.MINUTES.between(hEntradaHorario, hSalidaHorario);
            if (horasProgramadas > minutosDescanso && isShiftWithBreak(jornada.getTurno())) {
                horasProgramadas -= minutosDescanso;
            }
        } else {
            totalMinutos = ChronoUnit.MINUTES.between(entradaReal, salidaReal);
            if (shouldDeductBreak(totalMinutos, tiempoAlmuerzoReal, jornada.getTurno())) {
                totalMinutos -= descansoADescontar;
            }
            totalMinutos = Math.max(0, totalMinutos);
            horasProgramadas = 8 * 60;
        }

        asignarResultados(jornada, totalMinutos, horasProgramadas);
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

    private boolean shouldDeductBreak(long totalMinutos, long tiempoAlmuerzoReal, Turno turno) {
        return (totalMinutos > (5 * 60) || tiempoAlmuerzoReal >= 0) && isShiftWithBreak(turno);
    }

    private boolean isShiftWithBreak(Turno turno) {
        return turno != Turno.NOCHE && turno != Turno.MADRUGADA;
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

    private LocalDateTime ajustarEntrada(LocalDateTime entradaReal, LocalDateTime hEntradaHorario) {
        if (entradaReal.isBefore(hEntradaHorario)) {
            long diff = ChronoUnit.MINUTES.between(entradaReal, hEntradaHorario);
            if (diff <= 40) return hEntradaHorario;
        }
        return entradaReal;
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
