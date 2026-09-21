package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El saldo por lote contra el que se compara lo contado al re-finalizar una toma reabierta.
 *
 * Al re-finalizar, el saldo tiene que ser el del PRIMER cierre: el instante en que se creó el
 * ajuste. Tomar el saldo de ahora absorbe en el ajuste todo lo que entró o salió entre los dos
 * cierres. Es el mismo defecto que el agregado (ver {@code InventarioGraphQLFinalizarTest}).
 */
class InventarioLoteServiceSaldosTest {

    private static final Long PRODUCTO = 2993L;
    private static final Long SUCURSAL = 6L;
    private static final Long LOTE = 41L;
    private static final LocalDateTime PRIMER_CIERRE = LocalDateTime.of(2026, 9, 3, 10, 25, 23);

    private MovimientoStockLoteService movimientoStockLoteService;
    private InventarioLoteService service;

    private static StockLoteDto fila(Long loteId, double cantidad) {
        return new StockLoteDto(loteId, PRODUCTO, SUCURSAL, "L-" + loteId, null, null, null, cantidad);
    }

    private static MovimientoStock ajuste(LocalDateTime creadoEn) {
        MovimientoStock ajuste = new MovimientoStock();
        ajuste.setId(23871559L);
        ajuste.setSucursalId(SUCURSAL);
        ajuste.setCreadoEn(creadoEn);
        return ajuste;
    }

    @BeforeEach
    void setUp() {
        movimientoStockLoteService = mock(MovimientoStockLoteService.class);
        // Ahora: el lote recibió una transferencia después del primer cierre.
        when(movimientoStockLoteService.stockPorLote(PRODUCTO, SUCURSAL))
                .thenReturn(Collections.singletonList(fila(LOTE, 1402.0)));
        // Al primer cierre, sin el ajuste (que se creó en ese mismo instante).
        when(movimientoStockLoteService.stockPorLoteAntesDe(PRODUCTO, SUCURSAL, PRIMER_CIERRE))
                .thenReturn(Collections.singletonList(fila(LOTE, 355.0)));
        when(movimientoStockLoteService.findByMovimientoStock(anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());

        service = new InventarioLoteService();
        ReflectionTestUtils.setField(service, "movimientoStockLoteService", movimientoStockLoteService);
    }

    @Test
    @DisplayName("al re-finalizar, el saldo por lote es el del primer cierre y no el de ahora")
    void reFinalizarUsaElSaldoDelPrimerCierre() {
        Map<Long, Double> saldos = service.saldosPorLote(PRODUCTO, SUCURSAL, ajuste(PRIMER_CIERRE));

        assertEquals(355.0, saldos.get(LOTE));
        verify(movimientoStockLoteService, never()).stockPorLote(PRODUCTO, SUCURSAL);
    }

    @Test
    @DisplayName("un ajuste viejo sin creadoEn sigue con el saldo de ahora menos su desglose")
    void ajusteSinFechaSigueComoAntes() {
        Map<Long, Double> saldos = service.saldosPorLote(PRODUCTO, SUCURSAL, ajuste(null));

        assertEquals(1402.0, saldos.get(LOTE));
        verify(movimientoStockLoteService).findByMovimientoStock(23871559L, SUCURSAL);
    }

    @Test
    @DisplayName("el primer cierre, sin ajuste previo, usa el saldo de ahora")
    void primerCierreUsaElSaldoDeAhora() {
        Map<Long, Double> saldos = service.saldosPorLote(PRODUCTO, SUCURSAL, null);

        assertEquals(1402.0, saldos.get(LOTE));
    }
}
