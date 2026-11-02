package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Lotes flexibles, fase 1: una transferencia nunca pierde unidades del libro de lotes.
 *
 * Lo que FEFO no cubre con lotes reales viaja como "SIN LOTE" (lote nulo) en origen y destino, y
 * si la pantalla manda dos veces el mismo lote se suman en vez de chocar contra la clave única.
 */
class TransferenciaSinLoteTest {

    private static LoteFefoService.AsignacionLote a(Long loteId, String numero, double cantidad) {
        return new LoteFefoService.AsignacionLote(loteId, numero, cantidad);
    }

    @Test
    void loQueFefoNoCubreQuedaComoSinLote() {
        // Caso de la prueba en pantalla del 21/10: 80 unidades con 45 + 4 en lotes.
        List<LoteFefoService.AsignacionLote> r = MovimientoStockService.completarSinLote(
                Arrays.asList(a(1L, "L2610A", 45), a(2L, "L2611A", 4)), 80);
        assertEquals(3, r.size());
        assertNull(r.get(2).getLoteId());
        assertEquals(LoteFefoService.NUMERO_LOTE_SIN_TRAZAR, r.get(2).getNumeroLote());
        assertEquals(31.0, r.get(2).getCantidad(), 0.0001);
    }

    @Test
    void sinNingunLoteTodoEsSinLote() {
        List<LoteFefoService.AsignacionLote> r = MovimientoStockService.completarSinLote(
                Collections.emptyList(), 12);
        assertEquals(1, r.size());
        assertNull(r.get(0).getLoteId());
        assertEquals(12.0, r.get(0).getCantidad(), 0.0001);
    }

    @Test
    void siLosLotesAlcanzanNoAgregaSinLote() {
        List<LoteFefoService.AsignacionLote> r = MovimientoStockService.completarSinLote(
                Arrays.asList(a(1L, "L1", 30), a(2L, "L2", 20)), 50);
        assertEquals(2, r.size());
    }

    @Test
    void elDestinoRecibeTambienLaParteSinLote() {
        Lote lote = new Lote();
        lote.setId(1L);
        MovimientoStockLote conLote = new MovimientoStockLote();
        conLote.setLote(lote);
        conLote.setNumeroLote("L2610A");
        conLote.setCantidad(-45.0);
        MovimientoStockLote sinLote = new MovimientoStockLote();
        sinLote.setLote(null);
        sinLote.setNumeroLote(LoteFefoService.NUMERO_LOTE_SIN_TRAZAR);
        sinLote.setCantidad(-35.0);

        List<LoteFefoService.AsignacionLote> r = MovimientoStockService.recortarAsignaciones(
                Arrays.asList(conLote, sinLote), 80.0);
        assertEquals(2, r.size());
        assertEquals(1L, r.get(0).getLoteId());
        assertNull(r.get(1).getLoteId());
        assertEquals(35.0, r.get(1).getCantidad(), 0.0001);

        // Recepción parcial: lo que no llegó se descuenta primero de lo último (la parte sin lote).
        List<LoteFefoService.AsignacionLote> parcial = MovimientoStockService.recortarAsignaciones(
                Arrays.asList(conLote, sinLote), 50.0);
        assertEquals(45.0, parcial.get(0).getCantidad(), 0.0001);
        assertEquals(5.0, parcial.get(1).getCantidad(), 0.0001);
    }

    @Test
    void elMismoLoteDosVecesSeSuma() {
        List<TransferenciaItemLoteService.AsignacionSolicitada> r = TransferenciaItemLoteService.sumarPorLote(
                Arrays.asList(
                        new TransferenciaItemLoteService.AsignacionSolicitada(7L, 10.0),
                        new TransferenciaItemLoteService.AsignacionSolicitada(9L, 4.0),
                        new TransferenciaItemLoteService.AsignacionSolicitada(7L, 5.0),
                        new TransferenciaItemLoteService.AsignacionSolicitada(null, 3.0),
                        new TransferenciaItemLoteService.AsignacionSolicitada(9L, 0.0)));
        assertEquals(2, r.size());
        assertEquals(7L, r.get(0).getLoteId());
        assertEquals(15.0, r.get(0).getCantidad(), 0.0001);
        assertEquals(9L, r.get(1).getLoteId());
        assertEquals(4.0, r.get(1).getCantidad(), 0.0001);
    }
}
