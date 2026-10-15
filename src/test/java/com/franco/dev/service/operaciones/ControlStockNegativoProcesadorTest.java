package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El tramo que se re-lee nunca retrocede mas alla de donde arranco el control: no hay carga
 * retroactiva. Dentro de ese tramo, el filtro por fecha (15 minutos) lo aplica el SQL.
 */
class ControlStockNegativoProcesadorTest {

    @Test
    void miraHastaElSolapamientoDeIdsHaciaAtras() {
        assertEquals(100_000 - ControlStockNegativoProcesador.SOLAPE_IDS,
                ControlStockNegativoProcesador.rangoDesde(100_000, 1_000));
    }

    @Test
    void elSolapamientoNoPasaPorDebajoDelInicio() {
        assertEquals(99_000, ControlStockNegativoProcesador.rangoDesde(100_000, 99_000));
        assertEquals(100_000, ControlStockNegativoProcesador.rangoDesde(100_000, 100_000));
    }

    @Test
    void laAntiguedadMaximaEsDeSieteDias() {
        assertEquals(7, ControlStockNegativoProcesador.ANTIGUEDAD_MAXIMA_DIAS);
    }
}
