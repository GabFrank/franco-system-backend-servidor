package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.ProductoVencimiento;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.operaciones.enums.TipoOrigenVencimiento;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stock por lote de las devoluciones: que la baja, su reversion, el canje y la reversion del
 * canje lleguen al ledger por lote, y a que lote. Unit tests con Mockito (sin DB), con el
 * {@link DevolucionLoteService} real y sus colaboradores simulados.
 */
class DevolucionLoteTest {

    private static final long ORIGEN = 1L;
    private static final long DEPOSITO = 13L;
    private static final long ITEM = 5L;

    private DevolucionRepository repo;
    private DevolucionItemService itemService;
    private MovimientoStockService movimientoStockService;
    private ProductoVencimientoService productoVencimientoService;
    private LoteFefoService loteFefoService;
    private MovimientoStockLoteService movimientoStockLoteService;
    private LoteService loteService;
    private ApplicationContext ctx;
    private DevolucionService service;
    private final AtomicLong idMovimiento = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        itemService = mock(DevolucionItemService.class);
        movimientoStockService = mock(MovimientoStockService.class);
        // Como el servicio real: al guardar, el movimiento sale con id.
        when(movimientoStockService.save(any())).thenAnswer(inv -> {
            MovimientoStock m = inv.getArgument(0);
            m.setId(idMovimiento.incrementAndGet());
            return m;
        });
        productoVencimientoService = mock(ProductoVencimientoService.class);
        loteFefoService = mock(LoteFefoService.class);
        movimientoStockLoteService = mock(MovimientoStockLoteService.class);
        loteService = mock(LoteService.class);

        ctx = mock(ApplicationContext.class);
        DevolucionConfiguracion config = new DevolucionConfiguracion();
        config.setPermitirStockNegativo(true);
        DevolucionConfiguracionService configService = mock(DevolucionConfiguracionService.class);
        when(configService.getConfiguracion()).thenReturn(config);
        when(ctx.getBean(DevolucionConfiguracionService.class)).thenReturn(configService);
        when(ctx.getBean(DevolucionLoteService.class)).thenReturn(
                new DevolucionLoteService(loteFefoService, movimientoStockLoteService, loteService));

        service = new DevolucionService(repo, itemService, null, movimientoStockService,
                productoVencimientoService, null, null, null, null, ctx);
    }

    private Sucursal sucursal(long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        return s;
    }

    private Devolucion devolucion(DevolucionEstado estado, long ubicacion) {
        Devolucion d = new Devolucion();
        d.setId(1L);
        d.setTipo(TipoDevolucion.CON_PROVEEDOR);
        d.setEstado(estado);
        d.setSucursalOrigen(sucursal(ORIGEN));
        d.setSucursalUbicacion(sucursal(ubicacion));
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        return d;
    }

    private Lote lote(long id, String numero) {
        Lote l = new Lote();
        l.setId(id);
        l.setNumeroLote(numero);
        l.setEstado(EstadoLote.LIBERADO);
        return l;
    }

    /** Item en cajas x6 de un producto que lleva (o no) control de lote. */
    private DevolucionItem item(boolean conLote, double cantidad) {
        Producto producto = new Producto();
        producto.setId(32L);
        producto.setDescripcion("TIRZEDRAL");
        producto.setLote(conLote);
        Presentacion caja = new Presentacion();
        caja.setCantidad(6.0);
        DevolucionItem item = new DevolucionItem();
        item.setId(ITEM);
        item.setProducto(producto);
        item.setPresentacion(caja);
        item.setCantidad(cantidad);
        when(itemService.findByDevolucionId(1L)).thenReturn(new ArrayList<>(Collections.singletonList(item)));
        return item;
    }

    private MovimientoStockLote fila(Lote lote, double cantidad) {
        MovimientoStockLote f = new MovimientoStockLote();
        f.setLote(lote);
        f.setNumeroLote(lote.getNumeroLote());
        f.setCantidad(cantidad);
        return f;
    }

    /** Lo que se anoto en el ledger: lote -> cantidad, y el signo. */
    private List<LoteFefoService.AsignacionLote> desgloseAnotado(int signoEsperado) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LoteFefoService.AsignacionLote>> asignaciones = ArgumentCaptor.forClass(List.class);
        verify(movimientoStockLoteService).reemplazarDesglose(
                any(MovimientoStock.class), any(Producto.class), asignaciones.capture(), eq(ITEM), eq(signoEsperado));
        return asignaciones.getValue();
    }

    // ===================== separar =====================

    @Test
    void separar_conLoteElegido_bajaEnteraDeEseLote() {
        devolucion(DevolucionEstado.PENDIENTE, ORIGEN);
        DevolucionItem item = item(true, 2.0);
        Lote elegido = lote(7L, "L-7");
        // Bloqueado y sin saldo conocido: igual sale de ese lote, es lo que se devuelve.
        elegido.setEstado(EstadoLote.BLOQUEADO);
        item.setLoteMaestro(elegido);

        service.avanzarEstado(1L, DevolucionEstado.SEPARADO, null);

        List<LoteFefoService.AsignacionLote> desglose = desgloseAnotado(-1);
        assertEquals(1, desglose.size());
        assertEquals(7L, desglose.get(0).getLoteId().longValue());
        assertEquals(12.0, desglose.get(0).getCantidad()); // 2 cajas x6, en unidad base
        verify(loteFefoService, never()).asignar(anyLong(), anyLong(), anyDouble());
        assertEquals(1001L, item.getMovimientoBajaId().longValue());
        verify(itemService).save(item);
    }

    @Test
    void separar_sinLoteElegido_reparteporFefo() {
        devolucion(DevolucionEstado.PENDIENTE, ORIGEN);
        item(true, 2.0);
        when(loteFefoService.asignar(32L, ORIGEN, 12.0)).thenReturn(Arrays.asList(
                new LoteFefoService.AsignacionLote(7L, "L-7", 8.0),
                new LoteFefoService.AsignacionLote(9L, "L-9", 4.0)));

        service.avanzarEstado(1L, DevolucionEstado.SEPARADO, null);

        List<LoteFefoService.AsignacionLote> desglose = desgloseAnotado(-1);
        assertEquals(2, desglose.size());
        assertEquals(9L, desglose.get(1).getLoteId().longValue());
    }

    @Test
    void separar_productoSinControlDeLote_noTocaElLedger() {
        devolucion(DevolucionEstado.PENDIENTE, ORIGEN);
        DevolucionItem item = item(false, 2.0);

        service.avanzarEstado(1L, DevolucionEstado.SEPARADO, null);

        verify(ctx, never()).getBean(DevolucionLoteService.class);
        verify(movimientoStockLoteService, never()).reemplazarDesglose(any(), any(), any(), any(), anyInt());
        assertNull(item.getMovimientoBajaId());
    }

    // ===================== revertir el separado =====================

    @Test
    void cancelarSeparado_vuelveALosMismosLotesDeLaBaja() {
        devolucion(DevolucionEstado.SEPARADO, ORIGEN);
        DevolucionItem item = item(true, 2.0);
        item.setMovimientoBajaId(500L);
        // La baja 500 habia salido de dos lotes. Despues hubo un canje revertido: su salida
        // tambien es un negativo DEVOLUCION con la misma referencia, y no es la que hay que espejar.
        when(movimientoStockLoteService.findByMovimientoStock(500L, ORIGEN)).thenReturn(Arrays.asList(
                fila(lote(7L, "L-7"), -8.0), fila(lote(9L, "L-9"), -4.0)));

        service.avanzarEstado(1L, DevolucionEstado.CANCELADA, null);

        List<LoteFefoService.AsignacionLote> desglose = desgloseAnotado(1);
        assertEquals(2, desglose.size());
        assertEquals(7L, desglose.get(0).getLoteId().longValue());
        assertEquals(8.0, desglose.get(0).getCantidad());
        assertEquals(4.0, desglose.get(1).getCantidad());
        assertNull(item.getMovimientoBajaId());
    }

    @Test
    void cancelarSeparado_sinBajaRegistrada_noTocaElLedger() {
        // Separada antes de que las devoluciones llevaran lote: no hay desglose que espejar.
        devolucion(DevolucionEstado.SEPARADO, ORIGEN);
        item(true, 2.0);

        service.avanzarEstado(1L, DevolucionEstado.CANCELADA, null);

        verify(movimientoStockLoteService, never()).reemplazarDesglose(any(), any(), any(), any(), anyInt());
    }

    // ===================== canje =====================

    @Test
    void canje_productoConLoteSinNumeroDeLote_rechazaSinMoverStock() {
        devolucion(DevolucionEstado.RETIRADO, DEPOSITO);
        DevolucionItem item = item(true, 2.0);
        item.setCantidadReingresada(1.0);
        item.setLoteReingreso("   ");

        assertThrows(GraphQLException.class,
                () -> service.avanzarEstado(1L, DevolucionEstado.CANJEADO, null));

        verify(movimientoStockService, never()).save(any());
    }

    @Test
    void canje_entraAlLoteQueEntregaElProveedor() {
        devolucion(DevolucionEstado.RETIRADO, DEPOSITO);
        DevolucionItem item = item(true, 2.0);
        item.setCantidadReingresada(1.0);
        item.setLoteReingreso("L-NUEVO");
        when(loteService.obtenerOCrear(eq(item.getProducto()), eq("L-NUEVO"), any(), any(), any(), any()))
                .thenReturn(lote(20L, "L-NUEVO"));

        service.avanzarEstado(1L, DevolucionEstado.CANJEADO, null);

        List<LoteFefoService.AsignacionLote> desglose = desgloseAnotado(1);
        assertEquals(20L, desglose.get(0).getLoteId().longValue());
        assertEquals(6.0, desglose.get(0).getCantidad()); // reingreso parcial: 1 caja de 2
        assertEquals(1001L, item.getMovimientoCanjeId().longValue());
    }

    // ===================== revertir el canje =====================

    private void ingresoDeCanje(double cantidadBase) {
        ProductoVencimiento pv = new ProductoVencimiento();
        pv.setCantidad(cantidadBase);
        pv.setSucursal(sucursal(DEPOSITO));
        when(productoVencimientoService.delete(pv)).thenReturn(true);
        when(productoVencimientoService.findByTipoOrigenAndOrigenId(TipoOrigenVencimiento.DEVOLUCION_CANJE, ITEM))
                .thenReturn(Collections.singletonList(pv));
    }

    @Test
    void revertirCanje_saleDelMismoLoteAlQueEntro() {
        devolucion(DevolucionEstado.CANJEADO, DEPOSITO);
        DevolucionItem item = item(true, 2.0);
        item.setMovimientoCanjeId(700L);
        ingresoDeCanje(6.0);
        when(movimientoStockLoteService.findByMovimientoStock(700L, DEPOSITO))
                .thenReturn(Collections.singletonList(fila(lote(20L, "L-NUEVO"), 6.0)));

        service.revertirEstado(1L, null);

        List<LoteFefoService.AsignacionLote> desglose = desgloseAnotado(-1);
        assertEquals(20L, desglose.get(0).getLoteId().longValue());
        assertEquals(6.0, desglose.get(0).getCantidad());
        assertNull(item.getMovimientoCanjeId());
    }

    @Test
    void revertirCanje_anteriorAlControlDeLote_noTocaElLedger() {
        devolucion(DevolucionEstado.CANJEADO, DEPOSITO);
        item(true, 2.0);
        ingresoDeCanje(6.0);

        service.revertirEstado(1L, null);

        verify(movimientoStockLoteService, never()).reemplazarDesglose(any(), any(), any(), any(), anyInt());
    }
}
