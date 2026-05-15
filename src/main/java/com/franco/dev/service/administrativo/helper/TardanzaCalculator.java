package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.enums.Turno;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Component
public class TardanzaCalculator {

    public void calcular(Jornada jornada) {
        LocalTime horaEntradaHorario = jornada.getHoraEntradaHorario();
        if (horaEntradaHorario == null || jornada.getMarcacionEntrada() == null) return;

        LocalDateTime entradaReal = jornada.getMarcacionEntrada().getFechaEntrada();
        if (entradaReal == null) return;

        long diff = 0;
        if (jornada.getTurno() == Turno.MADRUGADA) {
            LocalDateTime hEntradaOficial = jornada.getFecha().atTime(horaEntradaHorario);
            diff = ChronoUnit.MINUTES.between(hEntradaOficial, entradaReal);
        } else {
            LocalTime horaEntradaReal = entradaReal.toLocalTime();
            diff = ChronoUnit.MINUTES.between(horaEntradaHorario, horaEntradaReal);
        }

        jornada.setMinutosLlegadaTardia(Math.max(0, diff));
        
        calcularTardanzaAlmuerzo(jornada);
    }

    private void calcularTardanzaAlmuerzo(Jornada jornada) {
        if (jornada.getMarcacionSalidaAlmuerzo() == null || jornada.getMarcacionEntradaAlmuerzo() == null) {
            jornada.setMinutosLlegadaTardiaAlmuerzo(0L);
            return;
        }

        LocalDateTime salidaAlmuerzo = getFechaMarcacion(jornada.getMarcacionSalidaAlmuerzo());
        LocalDateTime entradaAlmuerzo = jornada.getMarcacionEntradaAlmuerzo().getFechaEntrada();

        if (entradaAlmuerzo != null && salidaAlmuerzo != null && entradaAlmuerzo.isAfter(salidaAlmuerzo)) {
            long tiempoAlmuerzoReal = ChronoUnit.MINUTES.between(salidaAlmuerzo, entradaAlmuerzo);
            long minutosDescanso = getMinutosDescanso(jornada);

            if (tiempoAlmuerzoReal > minutosDescanso) {
                jornada.setMinutosLlegadaTardiaAlmuerzo(tiempoAlmuerzoReal - minutosDescanso);
            } else {
                jornada.setMinutosLlegadaTardiaAlmuerzo(0L);
            }
        }
    }

    private LocalDateTime getFechaMarcacion(com.franco.dev.domain.administrativo.Marcacion m) {
        return m.getFechaSalida() != null ? m.getFechaSalida() : m.getFechaEntrada();
    }

    private long getMinutosDescanso(Jornada jornada) {
        if (jornada.getInicioDescansoHorario() != null && jornada.getFinDescansoHorario() != null) {
            long diff = ChronoUnit.MINUTES.between(jornada.getInicioDescansoHorario(), jornada.getFinDescansoHorario());
            return diff < 0 ? diff + 1440 : diff;
        }
        return 60; // Default
    }
}
