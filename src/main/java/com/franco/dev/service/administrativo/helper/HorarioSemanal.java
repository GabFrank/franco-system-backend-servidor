package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.enums.Dia;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Que horario rige cada dia (#523). Es la unica regla: la usan la marcacion
 * (HorarioResolver), la asistencia y el resumen de la lista de funcionarios.
 *
 * Es flexible a proposito: un dia puede tener todos los horarios que se quieran, de TODOS
 * o del dia, aunque se pisen (ej. TODOS Dia 08:00-17:00 y jueves Madrugada 17:00-01:00, o
 * dos turnos el miercoles). Al marcar gana el de entrada mas cercana a la hora en que
 * marco; en un empate, el que nombra el dia y despues el mas nuevo. Sin ninguno la
 * jornada queda sin horario y se mide contra la estandar de 8 h.
 */
public final class HorarioSemanal {

    public static final List<Dia> SEMANA = Collections.unmodifiableList(Arrays.asList(
            Dia.LUNES, Dia.MARTES, Dia.MIERCOLES, Dia.JUEVES, Dia.VIERNES, Dia.SABADO, Dia.DOMINGO));

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DIA_MIN = 24 * 60;

    private HorarioSemanal() {
    }

    public static Dia dia(DayOfWeek dayOfWeek) {
        return dayOfWeek == null ? null : SEMANA.get(dayOfWeek.getValue() - 1);
    }

    /** Sin dias cargados, o con TODOS: rige los dias que no tengan un horario propio. */
    public static boolean esGeneral(Horario h) {
        return h.getDias() == null || h.getDias().isEmpty() || h.getDias().contains(Dia.TODOS);
    }

    public static boolean nombraDia(Horario h, Dia dia) {
        return h.getDias() != null && h.getDias().contains(dia);
    }

    /** Los horarios que rigen ese dia: los que lo nombran y los de TODOS. */
    public static List<Horario> candidatos(List<Horario> horarios, Dia dia) {
        if (horarios == null || dia == null) return Collections.emptyList();
        return horarios.stream().filter(h -> nombraDia(h, dia) || esGeneral(h)).collect(Collectors.toList());
    }

    /**
     * El horario de ese dia cuya entrada queda mas cerca de {@code hora}. En un empate gana el
     * que nombra el dia sobre el de TODOS, y despues el primero de la lista (el mas nuevo).
     * Sin hora, el primero que nombra el dia, o si no hay, el primero.
     */
    public static Horario elegir(List<Horario> horarios, Dia dia, LocalTime hora) {
        List<Horario> c = candidatos(horarios, dia);
        if (c.isEmpty()) return null;
        Horario mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Horario h : c) {
            int distancia = hora == null ? 0
                    : h.getHoraEntrada() == null ? Integer.MAX_VALUE - 1 : distancia(hora, h.getHoraEntrada());
            boolean gana = mejor == null || distancia < mejorDistancia
                    || (distancia == mejorDistancia && nombraDia(h, dia) && !nombraDia(mejor, dia));
            if (gana) {
                mejor = h;
                mejorDistancia = distancia;
            }
        }
        return mejor;
    }

    /** Minutos entre dos horas, dando la vuelta a medianoche (23:50 y 00:10 estan a 20). */
    private static int distancia(LocalTime a, LocalTime b) {
        int d = Math.abs(minutos(a) - minutos(b));
        return Math.min(d, DIA_MIN - d);
    }

    private static int minutos(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    /** Ej.: "L-Mi Día 08:00-17:00 · J Madrugada 17:00-01:00". Dias libres no se nombran. */
    public static String resumen(List<Horario> horarios) {
        List<String> claves = new ArrayList<>();
        for (Dia d : SEMANA) {
            claves.add(candidatos(horarios, d).stream()
                    .sorted(Comparator.comparing(h -> h.getHoraEntrada() == null ? LocalTime.MIN : h.getHoraEntrada()))
                    .map(HorarioSemanal::clave)
                    .distinct()
                    .collect(Collectors.joining(" + ")));
        }
        List<String> partes = new ArrayList<>();
        int i = 0;
        while (i < SEMANA.size()) {
            int j = i;
            while (j + 1 < SEMANA.size() && claves.get(j + 1).equals(claves.get(i))) j++;
            if (!claves.get(i).isEmpty()) {
                String dias = (i == 0 && j == SEMANA.size() - 1) ? "Todos"
                        : i == j ? abreviatura(SEMANA.get(i))
                        : abreviatura(SEMANA.get(i)) + "-" + abreviatura(SEMANA.get(j));
                partes.add(dias + " " + claves.get(i));
            }
            i = j + 1;
        }
        return partes.isEmpty() ? null : String.join(" · ", partes);
    }

    public static String hora(LocalTime t) {
        return t == null ? null : t.format(HH_MM);
    }

    private static String clave(Horario h) {
        return turno(h) + " " + hora(h.getHoraEntrada()) + "-" + hora(h.getHoraSalida());
    }

    private static String turno(Horario h) {
        if (h.getTurno() == null) return "Sin turno";
        switch (h.getTurno()) {
            case DIA: return "Día";
            case NOCHE: return "Noche";
            case MADRUGADA: return "Madrugada";
            default: return h.getTurno().name();
        }
    }

    public static String nombre(Dia d) {
        switch (d) {
            case LUNES: return "lunes";
            case MARTES: return "martes";
            case MIERCOLES: return "miércoles";
            case JUEVES: return "jueves";
            case VIERNES: return "viernes";
            case SABADO: return "sábado";
            case DOMINGO: return "domingo";
            default: return "todos los días";
        }
    }

    private static String abreviatura(Dia d) {
        switch (d) {
            case LUNES: return "L";
            case MARTES: return "Ma";
            case MIERCOLES: return "Mi";
            case JUEVES: return "J";
            case VIERNES: return "V";
            case SABADO: return "S";
            case DOMINGO: return "D";
            default: return "";
        }
    }
}
