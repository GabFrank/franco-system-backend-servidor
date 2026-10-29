package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.ReasignarLoteInput;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReasignacionLoteServiceTest {

    private ReasignacionLoteService service;
    private MovimientoStockService movimientoStockService;
    private MovimientoStockLoteService movimientoStockLoteService;
    private LoteService loteService;
    private LoteFefoService loteFefoService;
    private SincronizacionFilialService sync;
    private NamedParameterJdbcTemplate jdbc;
    private Producto producto;
    private Lote a;
    private Lote b;

    @BeforeEach
    void setUp() {
        service = new ReasignacionLoteService();
        movimientoStockService = mock(MovimientoStockService.class);
        movimientoStockLoteService = mock(MovimientoStockLoteService.class);
        loteService = mock(LoteService.class);
        loteFefoService = mock(LoteFefoService.class);
        sync = mock(SincronizacionFilialService.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        ProductoService productoService = mock(ProductoService.class);
        ReflectionTestUtils.setField(service, "movimientoStockService", movimientoStockService);
        ReflectionTestUtils.setField(service, "movimientoStockLoteService", movimientoStockLoteService);
        ReflectionTestUtils.setField(service, "loteService", loteService);
        ReflectionTestUtils.setField(service, "loteFefoService", loteFefoService);
        ReflectionTestUtils.setField(service, "productoService", productoService);
        ReflectionTestUtils.setField(service, "usuarioService", mock(UsuarioService.class));
        ReflectionTestUtils.setField(service, "modificacionService", mock(ModificacionService.class));
        ReflectionTestUtils.setField(service, "sincronizacionFilialService", sync);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);

        producto = new Producto();
        producto.setId(100L);
        producto.setLote(true);
        producto.setDescripcion("T 36");
        when(productoService.findById(100L)).thenReturn(Optional.of(producto));
        a = lote(1L, "L-A", producto);
        b = lote(2L, "L-B", producto);
        when(loteService.findById(1L)).thenReturn(Optional.of(a));
        when(loteService.findById(2L)).thenReturn(Optional.of(b));
        saldos(10.0, 3.0);
        when(sync.lotesPresentesEnFilial(eq(4L), anyCollection())).thenReturn(2L);
        when(sync.atrasoSegundos(4L)).thenReturn(5.0);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(movimientoStockService.save(any(MovimientoStock.class))).thenAnswer(i -> {
            MovimientoStock m = i.getArgument(0);
            m.setId(77L);
            return m;
        });
    }

    private static Lote lote(Long id, String numero, Producto p) {
        Lote l = new Lote();
        l.setId(id);
        l.setNumeroLote(numero);
        l.setProducto(p);
        return l;
    }

    private void saldos(double saldoA, double saldoB) {
        List<StockLoteDto> s = new ArrayList<>();
        StockLoteDto da = new StockLoteDto();
        da.setLoteId(1L);
        da.setCantidadDisponible(saldoA);
        StockLoteDto db = new StockLoteDto();
        db.setLoteId(2L);
        db.setCantidadDisponible(saldoB);
        s.add(da);
        s.add(db);
        when(movimientoStockLoteService.stockPorLote(100L, 4L)).thenReturn(s);
    }

    private ReasignarLoteInput input(double cantidad) {
        ReasignarLoteInput in = new ReasignarLoteInput();
        in.setProductoId(100L);
        in.setSucursalId(4L);
        in.setLoteOrigenId(1L);
        in.setLoteDestinoId(2L);
        in.setCantidad(cantidad);
        in.setMotivo("venta 2301 salio con L-A");
        return in;
    }

    @Test
    void pasaUnidadesSinCambiarLaExistencia() {
        service.reasignar(input(4.0));

        ArgumentCaptor<MovimientoStock> mov = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockService).save(mov.capture());
        assertEquals(0.0, mov.getValue().getCantidad(), 1e-9);
        ArgumentCaptor<MovimientoStockLote> filas = ArgumentCaptor.forClass(MovimientoStockLote.class);
        verify(movimientoStockLoteService, times(2)).save(filas.capture());
        assertEquals(-4.0, filas.getAllValues().get(0).getCantidad(), 1e-9);
        assertEquals(1L, filas.getAllValues().get(0).getLote().getId());
        assertEquals(4.0, filas.getAllValues().get(1).getCantidad(), 1e-9);
        assertEquals(2L, filas.getAllValues().get(1).getLote().getId());
        assertEquals(77L, filas.getAllValues().get(0).getMovimientoStockId());
    }

    @Test
    void tomaElLockAntesDeLeerElSaldo() {
        service.reasignar(input(4.0));
        org.mockito.InOrder o = inOrder(loteFefoService, movimientoStockLoteService);
        o.verify(loteFefoService).bloquearSaldo(4L);
        o.verify(movimientoStockLoteService).stockPorLote(100L, 4L);
    }

    @Test
    void noDejaElOrigenNegativo() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(11.0)));
        assertTrue(e.getMessage().contains("tiene 10.0"));
        verify(movimientoStockService, never()).save(any());
    }

    @Test
    void motivoObligatorio() {
        ReasignarLoteInput in = input(1.0);
        in.setMotivo(" ");
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(in));
    }

    @Test
    void mismoLoteOCantidadCeroSeRechazan() {
        ReasignarLoteInput in = input(1.0);
        in.setLoteDestinoId(1L);
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(in));
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(0.0)));
    }

    @Test
    void loteDeOtroProductoSeRechaza() {
        Producto otro = new Producto();
        otro.setId(200L);
        b.setProducto(otro);
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(1.0)));
    }

    @Test
    void saldoCambiadoMientrasMirabaSeRechaza() {
        ReasignarLoteInput in = input(1.0);
        in.setSaldoEsperado(9.0);
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(in));
    }

    @Test
    void filialSinAlgunoDeLosLotesSeRechazaAunForzando() {
        when(sync.lotesPresentesEnFilial(eq(4L), anyCollection())).thenReturn(1L);
        ReasignarLoteInput in = input(1.0);
        in.setForzar(true);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.reasignar(in));
        assertTrue(e.getMessage().contains("todavía no tiene"));
        verify(movimientoStockService, never()).save(any());
    }

    @Test
    void filialQueNoRespondeSeRechaza() {
        when(sync.lotesPresentesEnFilial(eq(4L), anyCollection())).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(1.0)));
    }

    @Test
    void sucursalAtrasadaSeRechazaAunForzando() {
        when(sync.atrasoSegundos(4L)).thenReturn(900.0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(1.0)));
        assertTrue(e.getMessage().contains("no está sincronizada"));
        ReasignarLoteInput in = input(1.0);
        in.setForzar(true);
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> service.reasignar(in));
        assertTrue(e2.getMessage().contains("no está sincronizada"));
        verify(movimientoStockService, never()).save(any());
    }

    @Test
    void sinSenalDeSincronizacionSeRechaza() {
        when(sync.atrasoSegundos(4L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(1.0)));
    }

    @Test
    void vaciarUnLoteConVentasRecientesSeRechaza() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(2L);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(10.0)));
        assertTrue(e.getMessage().contains("tuvo ventas"));
        // Pasar una parte no lo vacía: se permite.
        service.reasignar(input(5.0));
        verify(movimientoStockService).save(any());
    }

    @Test
    void productoSinControlDeLoteSeRechaza() {
        producto.setLote(false);
        assertThrows(IllegalArgumentException.class, () -> service.reasignar(input(1.0)));
    }
}
