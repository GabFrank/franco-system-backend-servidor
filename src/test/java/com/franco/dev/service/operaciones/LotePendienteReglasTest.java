package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de cuanto se puede atribuir al completar un pendiente de lote (#429, fase 2, E3). */
class LotePendienteReglasTest {

    @Test
    void completarTotal() {
        assertDoesNotThrow(() -> LotePendienteService.validarAtribucion(10, 10, 10.0, 10.0));
    }

    @Test
    void parcialPermitido() {
        assertDoesNotThrow(() -> LotePendienteService.validarAtribucion(10, 6, 6.0, 6.0));
    }

    @Test
    void nuncaMasQueElSinLoteDisponible() {
        // El SIN LOTE es un pozo: si se vendieron 4 sin lote, quedan 6 aunque la recepcion trajo 10.
        assertThrows(IllegalArgumentException.class,
                () -> LotePendienteService.validarAtribucion(10, 6, 6.0, 7.0));
    }

    @Test
    void nuncaMasQueLoPendiente() {
        assertThrows(IllegalArgumentException.class,
                () -> LotePendienteService.validarAtribucion(4, 100, 100.0, 5.0));
    }

    @Test
    void yaResueltoSeRechaza() {
        // Doble completado: la segunda vez el remanente es 0.
        assertThrows(IllegalArgumentException.class,
                () -> LotePendienteService.validarAtribucion(0, 100, 100.0, 1.0));
    }

    @Test
    void saldoCambioMientrasCompletaba() {
        assertThrows(IllegalArgumentException.class,
                () -> LotePendienteService.validarAtribucion(10, 9, 10.0, 5.0));
    }

    @Test
    void sinSaldoEsperadoSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> LotePendienteService.validarAtribucion(10, 10, null, 5.0));
    }

    @Test
    void cantidadCeroONegativaSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> LotePendienteService.validarAtribucion(10, 10, 10.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> LotePendienteService.validarAtribucion(10, 10, 10.0, -1.0));
    }

    @Test
    void sincronizacion() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 24, 12, 0);
        assertFalse(LotePendienteService.sincronizacionVencida(ahora.minusMinutes(5), 15, ahora));
        assertTrue(LotePendienteService.sincronizacionVencida(ahora.minusMinutes(16), 15, ahora));
        // Sin dato se bloquea: no se sabe si hay ventas de la filial sin replicar.
        assertTrue(LotePendienteService.sincronizacionVencida(null, 15, ahora));
        // 0 = control apagado (entornos sin replicacion, como la copia local).
        assertFalse(LotePendienteService.sincronizacionVencida(null, 0, ahora));
    }
}
