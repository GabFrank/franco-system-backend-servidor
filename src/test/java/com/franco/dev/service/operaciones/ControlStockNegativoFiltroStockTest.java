package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.FiltroStockControl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El filtro de stock previo de la lista de control: en cero, negativo, o todos. Sin filtro no se
 * agrega ninguna condicion: todos los registros tienen stock previo menor o igual a cero.
 */
class ControlStockNegativoFiltroStockTest {

    @Test
    void sinFiltroNoAgregaCondicion() {
        assertEquals("", ControlStockNegativoService.condicionStock(null));
    }

    @Test
    void ceroFiltraLosDeStockExactamenteCero() {
        assertEquals(" and c.stockPrevio = 0", ControlStockNegativoService.condicionStock(FiltroStockControl.CERO));
    }

    @Test
    void negativoFiltraLosMenoresACero() {
        assertEquals(" and c.stockPrevio < 0", ControlStockNegativoService.condicionStock(FiltroStockControl.NEGATIVO));
    }
}
