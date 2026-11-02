package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lotes flexibles, fase 1 (compras): lo recibido sin lote de un producto con control de lote
 * queda como "SIN LOTE" en vez de desaparecer del libro de lotes.
 */
class RecepcionSinLoteTest {

    private MovimientoStockLoteService servicio() {
        MovimientoStockLoteService s = mock(MovimientoStockLoteService.class);
        doCallRealMethod().when(s).completarSinLoteCompra(any(), any(), any());
        when(s.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return s;
    }

    private RecepcionMercaderiaItem item(boolean conLote) {
        Producto p = new Producto();
        p.setLote(conLote);
        RecepcionMercaderiaItem it = new RecepcionMercaderiaItem();
        it.setProducto(p);
        return it;
    }

    private MovimientoStock mov(double cantidad) {
        MovimientoStock m = new MovimientoStock();
        m.setCantidad(cantidad);
        m.setSucursalId(1L);
        return m;
    }

    private MovimientoStockLote fila(double cantidad) {
        MovimientoStockLote l = new MovimientoStockLote();
        l.setCantidad(cantidad);
        return l;
    }

    @Test
    void sinLoteTodoQuedaComoSinLote() {
        List<MovimientoStockLote> creados = new ArrayList<>();
        servicio().completarSinLoteCompra(item(true), mov(10), creados);
        assertEquals(1, creados.size());
        assertNull(creados.get(0).getLote());
        assertEquals(LoteFefoService.NUMERO_LOTE_SIN_TRAZAR, creados.get(0).getNumeroLote());
        assertEquals(10.0, creados.get(0).getCantidad(), 0.0001);
    }

    @Test
    void dosLotesQueNoCubrenTodoCompletanConSinLote() {
        List<MovimientoStockLote> creados = new ArrayList<>();
        creados.add(fila(3));
        creados.add(fila(4));
        servicio().completarSinLoteCompra(item(true), mov(10), creados);
        assertEquals(3, creados.size());
        assertEquals(3.0, creados.get(2).getCantidad(), 0.0001);
    }

    @Test
    void siLosLotesCubrenTodoNoAgrega() {
        List<MovimientoStockLote> creados = new ArrayList<>();
        creados.add(fila(6));
        creados.add(fila(4));
        servicio().completarSinLoteCompra(item(true), mov(10), creados);
        assertEquals(2, creados.size());
    }

    @Test
    void productoSinControlDeLoteNoCambia() {
        List<MovimientoStockLote> creados = new ArrayList<>();
        servicio().completarSinLoteCompra(item(false), mov(10), creados);
        assertEquals(0, creados.size());
    }
}
