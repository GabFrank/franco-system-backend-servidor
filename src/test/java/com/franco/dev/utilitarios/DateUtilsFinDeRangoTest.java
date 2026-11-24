package com.franco.dev.utilitarios;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #744: la lista de ventas y la de facturas dejaban afuera el ultimo dia del rango porque la
 * fecha de fin sin hora se tomaba como las 00:00 de ese dia.
 */
class DateUtilsFinDeRangoTest {

    @Test
    void fechaSinHoraIncluyeElDiaCompleto() {
        // Hasta el ultimo microsegundo (la precision de timestamp en PostgreSQL): en bodega hay
        // ventas creadas a las 23:59:59.xxx que un fin a las 23:59:59 deja afuera.
        assertEquals(LocalDateTime.of(2025, 1, 31, 23, 59, 59, 999_999_000),
                DateUtils.stringToFinDeRango("2025-01-31"));
    }

    @Test
    void noRedondeaAlDiaSiguiente() {
        // pgjdbc redondea los nanos a microsegundos: con 999_999_999 pasaria a las 00:00 del dia
        // siguiente e incluiria lo creado justo a medianoche.
        LocalDateTime fin = DateUtils.stringToFinDeRango("2025-01-31");
        assertEquals(0, fin.getNano() % 1_000);
        assertEquals(31, fin.getDayOfMonth());
    }

    @Test
    void conHoraRespetaLaHoraPedida() {
        assertEquals(LocalDateTime.of(2026, 9, 30, 15, 30),
                DateUtils.stringToFinDeRango("2026-09-30 15:30"));
        assertEquals(LocalDateTime.of(2026, 9, 30, 23, 59, 59),
                DateUtils.stringToFinDeRango("2026-09-30 23:59:59"));
    }

    @Test
    void vacioONuloEsSinFiltro() {
        assertNull(DateUtils.stringToFinDeRango(null));
        assertNull(DateUtils.stringToFinDeRango("  "));
    }

    @Test
    void stringToDateNoCambia() {
        // El contrato de stringToDate lo usan muchas consultas: la fecha sola sigue siendo las 00:00.
        assertEquals(LocalDateTime.of(2025, 1, 31, 0, 0), DateUtils.stringToDate("2025-01-31"));
    }
}
