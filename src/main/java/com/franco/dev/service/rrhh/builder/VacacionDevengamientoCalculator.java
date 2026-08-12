package com.franco.dev.service.rrhh.builder;

/**
 * Cálculo puro de los días de vacaciones devengados según antigüedad (ley PY):
 *   &lt; 5 años  → 12 días
 *   5–10 años  → 18 días
 *   &gt; 10 años → 30 días
 * Los umbrales/valores son parametrizables (config RRHH). Sin dependencias de
 * Spring/JPA para poder testearlo de forma aislada.
 */
public final class VacacionDevengamientoCalculator {

    private VacacionDevengamientoCalculator() {
    }

    public static int diasPorAntiguedad(int anios, int diasHasta5, int dias5a10, int diasMas10) {
        if (anios < 5) return diasHasta5;
        if (anios <= 10) return dias5a10;
        return diasMas10;
    }
}
