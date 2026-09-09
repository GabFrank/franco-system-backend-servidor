package com.franco.dev.service.rrhh.builder;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Cálculo puro de los días que consume un período de vacaciones.
 *
 * <p>Acá se trabaja de lunes a sábado y el domingo es día libre, así que un período
 * de vacaciones descuenta del saldo solo los días laborables del rango: 12 días de
 * vacaciones abarcan 13 o 14 días corridos según cuántos domingos caigan en el medio.
 * Contarlos corridos hacía que una asignación válida de 12 días se rechazara con
 * "Los dias del periodo superan los dias disponibles".
 *
 * <p>Los feriados NO se excluyen: un feriado dentro de las vacaciones se consume como
 * día de vacación. Sin dependencias de Spring/JPA para poder testearlo aislado.
 */
public final class VacacionPeriodoCalculator {

    private VacacionPeriodoCalculator() {
    }

    /** Un día es laborable si no es domingo. */
    public static boolean esDiaLaborable(LocalDate fecha) {
        return fecha != null && fecha.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * Días laborables del rango [desde, hasta], ambos inclusive. Devuelve 0 si el rango
     * es nulo o está invertido.
     */
    public static int diasLaborables(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) return 0;
        int dias = 0;
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            if (esDiaLaborable(d)) dias++;
        }
        return dias;
    }
}
