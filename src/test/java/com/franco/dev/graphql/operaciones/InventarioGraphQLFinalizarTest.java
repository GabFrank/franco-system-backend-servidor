package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.operaciones.InventarioLoteService;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.InventarioService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.productos.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Finalizar una toma con items que nadie conto.
 *
 * `cantidad` —lo contado— es nullable: un item que se sumo a la toma y que
 * nadie fue a contar la tiene en null. Al finalizar se la multiplicaba sin
 * mirar, asi que reventaba con un NullPointerException al desempaquetar el
 * Double y NINGUNA toma con un item sin contar se podia finalizar. En bodega3
 * era el caso de la toma 7540: dos items, uno sin contar.
 */
class InventarioGraphQLFinalizarTest {

    private static final Long INVENTARIO = 7540L;
    private static final Long SUCURSAL = 3L;
    private static final Long CONTADO = 800L;
    private static final Long SIN_CONTAR = 900L;

    private InventarioService service;
    private InventarioProductoService inventarioProductoService;
    private InventarioProductoItemService itemService;
    private ProductoService productoService;
    private MovimientoStockService movimientoStockService;
    private InventarioLoteService inventarioLoteService;
    private InventarioGraphQL resolver;

    private InventarioProductoItem item(Long productoId, Double cantidadContada, double porPresentacion) {
        Producto producto = new Producto();
        producto.setId(productoId);

        Presentacion presentacion = new Presentacion();
        presentacion.setId(productoId * 10);
        presentacion.setCantidad(porPresentacion);
        presentacion.setProducto(producto);

        InventarioProductoItem item = new InventarioProductoItem();
        item.setId(productoId + 1);
        item.setPresentacion(presentacion);
        item.setCantidad(cantidadContada);
        return item;
    }

    @BeforeEach
    void setUp() {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);

        Inventario inventario = new Inventario();
        inventario.setId(INVENTARIO);
        inventario.setSucursal(sucursal);
        inventario.setEstado(InventarioEstado.ABIERTO);

        InventarioProducto zona = new InventarioProducto();
        zona.setId(91L);

        service = mock(InventarioService.class);
        when(service.findById(INVENTARIO)).thenReturn(Optional.of(inventario));

        inventarioProductoService = mock(InventarioProductoService.class);
        when(inventarioProductoService.findByInventarioId(INVENTARIO))
                .thenReturn(Collections.singletonList(zona));

        itemService = mock(InventarioProductoItemService.class);

        productoService = mock(ProductoService.class);
        when(productoService.findById(anyLong())).thenAnswer(invocacion -> {
            Producto p = new Producto();
            p.setId(invocacion.getArgument(0));
            return Optional.of(p);
        });

        movimientoStockService = mock(MovimientoStockService.class);
        when(movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(
                any(), anyLong(), anyLong(), anyLong())).thenReturn(null);
        when(movimientoStockService.stockByProductoIdAndSucursalId(anyLong(), anyLong())).thenReturn(10.0);
        when(movimientoStockService.save(any(MovimientoStock.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        inventarioLoteService = mock(InventarioLoteService.class);
        when(inventarioLoteService.contadoPorProductoYLote(INVENTARIO)).thenReturn(new HashMap<>());

        resolver = new InventarioGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "inventarioProductoService", inventarioProductoService);
        ReflectionTestUtils.setField(resolver, "inventarioProductoItemService", itemService);
        ReflectionTestUtils.setField(resolver, "productoService", productoService);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", movimientoStockService);
        ReflectionTestUtils.setField(resolver, "inventarioLoteService", inventarioLoteService);
    }

    /** El mismo item, pero de un producto CON control de lote y atribuido a un lote. */
    private InventarioProductoItem itemConLote(Long productoId, Double cantidadContada,
                                               double porPresentacion, Long loteId) {
        InventarioProductoItem item = item(productoId, cantidadContada, porPresentacion);
        item.getPresentacion().getProducto().setLote(true);
        Lote lote = new Lote();
        lote.setId(loteId);
        lote.setNumeroLote("L-" + loteId);
        item.setLote(lote);
        return item;
    }

    private void conProductoConLote(Long productoId) {
        when(productoService.findById(eq(productoId))).thenAnswer(invocacion -> {
            Producto p = new Producto();
            p.setId(invocacion.getArgument(0));
            p.setLote(true);
            return Optional.of(p);
        });
    }

    private void conContadoPorLote(Long productoId, Long loteId, Double unidades) {
        Map<Long, Double> porLote = new HashMap<>();
        porLote.put(loteId, unidades);
        Map<Long, Map<Long, Double>> porProducto = new HashMap<>();
        porProducto.put(productoId, porLote);
        when(inventarioLoteService.contadoPorProductoYLote(INVENTARIO)).thenReturn(porProducto);
    }

    private void conItems(List<InventarioProductoItem> items) {
        when(itemService.findByInventarioProductoId(91L)).thenReturn(new ArrayList<>(items));
    }

    @Test
    @DisplayName("un item sin contar no impide finalizar la toma")
    void itemSinContarNoRompe() {
        // Es el caso de la toma 7540 de bodega3: dos items, uno sin contar.
        conItems(Arrays.asList(item(CONTADO, 7.0, 1.0), item(SIN_CONTAR, null, 1.0)));

        assertDoesNotThrow(() -> resolver.finalizarInventarioEnSucursal(INVENTARIO));
    }

    @Test
    @DisplayName("el producto sin contar no entra en el ajuste, y NO se le lleva el stock a cero")
    void elSinContarNoAjusta() {
        // Tomarlo como cero contra un stock de 10 daria una diferencia de -10:
        // una perdida de stock muda, sin que nadie hubiera contado nada.
        conItems(Arrays.asList(item(CONTADO, 7.0, 1.0), item(SIN_CONTAR, null, 1.0)));

        resolver.finalizarInventarioEnSucursal(INVENTARIO);

        ArgumentCaptor<MovimientoStock> guardados = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockService).save(guardados.capture());
        MovimientoStock unico = guardados.getValue();
        assertEquals(CONTADO, unico.getProducto().getId());
        // 7 contados contra 10 del sistema: faltan 3.
        assertEquals(-3.0, unico.getCantidad());
    }

    @Test
    @DisplayName("una toma con todos sus items sin contar no mueve stock")
    void todosSinContarNoMueveNada() {
        conItems(Collections.singletonList(item(SIN_CONTAR, null, 1.0)));

        resolver.finalizarInventarioEnSucursal(INVENTARIO);

        verify(movimientoStockService, never()).save(any(MovimientoStock.class));
    }

    @Test
    @DisplayName("lo contado se convierte a unidades del producto por la presentacion")
    void seConvierteAUnidades() {
        // Una caja x 6 contada 2 veces son 12 unidades, no 2.
        conItems(Collections.singletonList(item(CONTADO, 2.0, 6.0)));

        resolver.finalizarInventarioEnSucursal(INVENTARIO);

        ArgumentCaptor<MovimientoStock> guardados = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockService).save(guardados.capture());
        assertEquals(2.0, guardados.getValue().getCantidad());
    }

    @Test
    @DisplayName("contar cero SI ajusta: cero es un conteo, null no")
    void ceroSiCuenta() {
        // Es la distincion que hace todo esto: cero dice «no hay nada en la
        // gondola», null dice «nadie fue a mirar».
        conItems(Collections.singletonList(item(CONTADO, 0.0, 1.0)));

        resolver.finalizarInventarioEnSucursal(INVENTARIO);

        ArgumentCaptor<MovimientoStock> guardados = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockService).save(guardados.capture());
        assertEquals(-10.0, guardados.getValue().getCantidad());
    }

    @Test
    @DisplayName("un producto con lote escribe el desglose en el ledger, no solo el agregado")
    void productoConLoteEscribeDesglose() {
        // Es el punto entero del cambio: sin esto el ajuste queda en el agregado y la mercaderia
        // contada nunca vuelve a ser asignable por FEFO.
        conProductoConLote(CONTADO);
        conContadoPorLote(CONTADO, 41L, 7.0);
        when(inventarioLoteService.saldosPorLote(eq(CONTADO), eq(SUCURSAL), any()))
                .thenReturn(saldos(41L, 10.0));
        conItems(Collections.singletonList(itemConLote(CONTADO, 7.0, 1.0, 41L)));

        resolver.finalizarInventarioEnSucursal(INVENTARIO);

        verify(inventarioLoteService).escribirDesglose(any(MovimientoStock.class), any(Producto.class),
                any(), any());
    }

    @Test
    @DisplayName("un lote con saldo que nadie conto deja al producto ENTERO fuera del ajuste")
    void loteSinContarDejaAlProductoAfuera() {
        // Ni movimiento agregado ni filas del ledger: la misma regla que rige para el item con
        // cantidad nula. Tomar el lote como cero le borraria el stock a mercaderia que nadie miro.
        conProductoConLote(CONTADO);
        conContadoPorLote(CONTADO, 41L, 7.0);
        // El lote 42 tiene 20 unidades y ningun renglon lo conto.
        when(inventarioLoteService.saldosPorLote(eq(CONTADO), eq(SUCURSAL), any()))
                .thenReturn(saldos(41L, 10.0, 42L, 20.0));
        conItems(Collections.singletonList(itemConLote(CONTADO, 7.0, 1.0, 41L)));

        resolver.finalizarInventarioEnSucursal(INVENTARIO);

        verify(movimientoStockService, never()).save(any(MovimientoStock.class));
    }

    @Test
    @DisplayName("un producto sin control de lote no toca el ledger por lote")
    void productoSinLoteNoTocaElLedger() {
        // Regresion cero para los ~8.700 productos que no llevan lote.
        conItems(Collections.singletonList(item(CONTADO, 7.0, 1.0)));

        resolver.finalizarInventarioEnSucursal(INVENTARIO);

        verify(movimientoStockService).save(any(MovimientoStock.class));
        verify(inventarioLoteService, never()).escribirDesglose(any(), any(), any(), any());
    }

    private static Map<Long, Double> saldos(Object... pares) {
        Map<Long, Double> mapa = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put(((Number) pares[i]).longValue(), ((Number) pares[i + 1]).doubleValue());
        }
        return mapa;
    }

    @Test
    @DisplayName("un inventario que no existe lo dice, en vez de reventar con un NullPointer")
    void inventarioInexistente() {
        when(service.findById(anyLong())).thenReturn(Optional.empty());

        Exception error = assertThrows(Exception.class,
                () -> resolver.finalizarInventarioEnSucursal(123L));
        assertTrue(error.getMessage().contains("123"), error.getMessage());
    }

    @Test
    @DisplayName("la toma queda concluida y cerrada")
    void quedaConcluida() {
        conItems(Collections.singletonList(item(CONTADO, 7.0, 1.0)));

        Inventario finalizado = resolver.finalizarInventarioEnSucursal(INVENTARIO);

        assertEquals(InventarioEstado.CONCLUIDO, finalizado.getEstado());
        assertEquals(Boolean.FALSE, finalizado.getAbierto());
    }
}
