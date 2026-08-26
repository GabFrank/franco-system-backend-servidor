package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * La normalizacion del numero de lote.
 *
 * Es lo unico que evita que " lote2026101 " y "LOTE2026101" terminen siendo dos lotes distintos con
 * el mismo stock repartido. Vive en el backend y no solo en la UI porque el conteo del telefono, la
 * recepcion del escritorio y una llamada directa al GraphQL son tres puertas independientes.
 */
class LoteNumeroTest {

    @Test
    void colapsaEspaciosYMinusculas() {
        assertEquals("LOTE2026101", LoteService.normalizarNumeroLote("  lote2026101 "));
    }

    @Test
    void unNumeroVacioNoEsUnLote() {
        // Crear un lote llamado "" dejaria un maestro que nadie puede volver a encontrar.
        assertNull(LoteService.normalizarNumeroLote("   "));
        assertNull(LoteService.normalizarNumeroLote(null));
    }

    @Test
    void respetaLosGuionesYLosCeros() {
        // Un numero de lote no es un numero: "L-007" y "L-7" son lotes distintos.
        assertEquals("L-007", LoteService.normalizarNumeroLote("l-007"));
    }
}
