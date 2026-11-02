package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.franco.dev.service.operaciones.compras.FacturaRepetida.Veredicto.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Casos de GENFI (timbrado 16905763, factura 0055357 del 20/10/2026 por 46.218.002). */
class FacturaRepetidaTest {

    private static final LocalDate EMISION = LocalDate.of(2026, 10, 20);

    @Test
    void mismoTimbradoYNumeroEsLaMismaFactura() {
        assertEquals(MISMA, FacturaRepetida.comparar(16905763, EMISION, 46218002.0, 16905763, LocalDate.of(2026, 10, 25), 1.0));
    }

    @Test
    void otroTimbradoConElMismoNumeroEsOtraFactura() {
        assertEquals(DISTINTA, FacturaRepetida.comparar(16905763, EMISION, 46218002.0, 15000001, EMISION, 46218002.0));
    }

    @Test
    void cargadaAManoSinTimbradoConLaMismaFechaEsLaMisma() {
        assertEquals(MISMA, FacturaRepetida.comparar(16905763, EMISION, 46218002.0, null, EMISION, 40000000.0));
    }

    @Test
    void cargadaAManoSinTimbradoConElMismoMontoEsLaMisma() {
        // El desktop guardó 46.218.002,052 y se cargó con fecha de recepción
        assertEquals(MISMA, FacturaRepetida.comparar(16905763, EMISION, 46218002.0, null, LocalDate.of(2026, 10, 22), 46218002.052));
    }

    @Test
    void sinTimbradoNiFechaNiMontoQueCoincidanSoloSeAvisa() {
        assertEquals(POSIBLE, FacturaRepetida.comparar(16905763, EMISION, 46218002.0, null, LocalDate.of(2025, 3, 1), 330670.0));
    }
}
