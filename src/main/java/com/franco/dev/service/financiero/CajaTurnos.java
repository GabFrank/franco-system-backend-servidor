package com.franco.dev.service.financiero;

import com.franco.dev.domain.administrativo.enums.Turno;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turnos de apertura de caja para el filtro de la lista de Cajas, iguales para bodega y farmacia:
 * MADRUGADA desde las 23:00, DIA desde las 04:00, NOCHE desde las 12:30, cada uno hasta el corte siguiente.
 *
 * Los cortes salen de las aperturas y los cierres reales de 2025-11 a 2026-11: quien abre desde las 12:30
 * cierra de noche (en las dos redes), quien abre desde las 23:00 cierra a la mañana, y entre las 03:00 y
 * las 04:00 no abre nadie.
 */
@Component
public class CajaTurnos {

    /** Corrimiento que lleva las 23:00 al minuto 0, para que cada turno sea un rango continuo. */
    static final int OFFSET = 60;

    private final String zona;

    public CajaTurnos(@Value("${app.timezone:America/Asuncion}") String zona) {
        this.zona = zona;
    }

    /** Zona en que se leen las horas de apertura: la del negocio, no la de la sesión de Postgres. */
    public String zona() {
        return zona;
    }

    /**
     * {offset, desde, hasta} para el JPQL: con r = (minuto del día + offset) mod 1440, el turno es
     * desde ≤ r &lt; hasta. Sin turno, todo el día.
     */
    public static int[] rango(Turno turno) {
        if (turno == null) return new int[]{OFFSET, 0, 1440};
        switch (turno) {
            case MADRUGADA: return new int[]{OFFSET, 0, 300};   // 23:00 a 04:00
            case DIA: return new int[]{OFFSET, 300, 810};       // 04:00 a 12:30
            default: return new int[]{OFFSET, 810, 1440};       // NOCHE: 12:30 a 23:00
        }
    }
}
