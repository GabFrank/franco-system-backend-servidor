package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.ProductoVencimiento;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoOrigenVencimiento;
import com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controles de stock de las devoluciones: tope y reversion del canje, tipo de los
 * movimientos y edicion de la cabecera. Unit tests con Mockito (sin DB).
 *
 * Cada caso reproduce un descuadre visto en la prueba de runtime del 2026-10-13:
 * canje que reingreso 5 cajas habiendo devuelto 2, canje confirmado sin reingreso,
 * canje imposible de deshacer, y cambio de sucursal de origen despues de separar.
 */
class DevolucionControlesStockTest {

    private static final long ORIGEN = 1L;
    private static final long DEPOSITO = 13L;

    private DevolucionRepository repo;
    private DevolucionItemService itemService;
    private MovimientoStockService movimientoStockService;
    private ProductoVencimientoService productoVencimientoService;
    private ApplicationContext ctx;
    private DevolucionConfiguracion config;
    private DevolucionService service;

    @BeforeEach
    void setUp() {
        repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        itemService = mock(DevolucionItemService.class);
        movimientoStockService = mock(MovimientoStockService.class);
        productoVencimientoService = mock(ProductoVencimientoService.class);
        ctx = mock(ApplicationContext.class);
        config = new DevolucionConfiguracion();
        config.setPermitirStockNegativo(true);
        DevolucionConfiguracionService configService = mock(DevolucionConfiguracionService.class);
        when(configService.getConfiguracion()).thenReturn(config);
        when(ctx.getBean(DevolucionConfiguracionService.class)).thenReturn(configService);
        service = new DevolucionService(repo, itemService, null, movimientoStockService,
                productoVencimientoService, null, null, null, null, ctx);
    }

    private Sucursal sucursal(long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        return s;
    }

    /** Devolucion con proveedor, origen en ORIGEN y colectada en DEPOSITO. */
    private Devolucion devolucion(DevolucionEstado estado) {
        Devolucion d = new Devolucion();
        d.setId(1L);
        d.setTipo(TipoDevolucion.CON_PROVEEDOR);
        d.setEstado(estado);
        d.setSucursalOrigen(sucursal(ORIGEN));
        d.setSucursalUbicacion(sucursal(DEPOSITO));
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        return d;
    }

    /** Item en cajas x6: cantidad y reingreso en cajas, el stock se mueve en unidades. */
    private DevolucionItem item(long id, double cantidad, Double reingresada) {
        Producto producto = new Producto();
        producto.setId(32L);
        producto.setDescripcion("CORONITA");
        Presentacion caja = new Presentacion();
        caja.setCantidad(6.0);
        DevolucionItem item = new DevolucionItem();
        item.setId(id);
        item.setProducto(producto);
        item.setPresentacion(caja);
        item.setCantidad(cantidad);
        item.setCantidadReingresada(reingresada);
        return item;
    }

    private void items(DevolucionItem... items) {
        when(itemService.findByDevolucionId(1L)).thenReturn(new ArrayList<>(Arrays.asList(items)));
    }

    private ProductoVencimiento ingresoDeCanje(DevolucionItem item, double cantidadBase, long sucursalId) {
        ProductoVencimiento pv = new ProductoVencimiento();
        pv.setCantidad(cantidadBase);
        pv.setSucursal(sucursal(sucursalId));
        when(productoVencimientoService.delete(pv)).thenReturn(true);
        when(productoVencimientoService.findByTipoOrigenAndOrigenId(
                TipoOrigenVencimiento.DEVOLUCION_CANJE, item.getId()))
                .thenReturn(Collections.singletonList(pv));
        return pv;
    }

    private List<MovimientoStock> movimientos() {
        ArgumentCaptor<MovimientoStock> captor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientoStockService).save(captor.capture());
        return captor.getAllValues();
    }

    // ===================== canje =====================

    @Test
    void canje_reingresoMayorALoDevuelto_rechazaSinMoverStock() {
        devolucion(DevolucionEstado.RETIRADO);
        items(item(5L, 2.0, 5.0));

        assertThrows(GraphQLException.class,
                () -> service.avanzarEstado(1L, DevolucionEstado.CANJEADO, null));

        verify(movimientoStockService, never()).save(any());
        verify(productoVencimientoService, never()).save(any());
        verify(repo, never()).save(any());
    }

    @Test
    void canje_sinNingunReingreso_rechazaSinMoverStock() {
        devolucion(DevolucionEstado.RETIRADO);
        items(item(7L, 2.0, null), item(8L, 1.0, 0.0));

        assertThrows(GraphQLException.class,
                () -> service.avanzarEstado(1L, DevolucionEstado.CANJEADO, null));

        verify(movimientoStockService, never()).save(any());
        verify(repo, never()).save(any());
    }

    @Test
    void canje_parcial_reingresaLoCargadoEnLaUbicacionComoDevolucion() {
        devolucion(DevolucionEstado.RETIRADO);
        items(item(5L, 2.0, 1.0));

        Devolucion res = service.avanzarEstado(1L, DevolucionEstado.CANJEADO, null);

        assertEquals(DevolucionEstado.CANJEADO, res.getEstado());
        MovimientoStock m = movimientos().get(0);
        assertEquals(6.0, m.getCantidad()); // 1 caja x6
        assertEquals(DEPOSITO, m.getSucursalId().longValue()); // entra donde esta, no en el origen
        assertEquals(TipoMovimiento.DEVOLUCION, m.getTipoMovimiento());
        assertEquals(5L, m.getReferencia().longValue());
    }

    // ===================== revertir canje =====================

    @Test
    void revertirCanje_sacaLoQueEntro_yVuelveARetirado() {
        Devolucion d = devolucion(DevolucionEstado.CANJEADO);
        d.setResolucion(TipoResolucionDevolucion.CANJE);
        d.setFinalizado(true);
        // Canje anterior al tope: devolvio 2 cajas y reingreso 5 (30 unidades).
        DevolucionItem item = item(5L, 2.0, 5.0);
        items(item);
        ProductoVencimiento pv = ingresoDeCanje(item, 30.0, DEPOSITO);

        Devolucion res = service.revertirEstado(1L, null);

        assertEquals(DevolucionEstado.RETIRADO, res.getEstado());
        assertNull(res.getResolucion());
        assertFalse(res.getFinalizado());
        MovimientoStock m = movimientos().get(0);
        assertEquals(-30.0, m.getCantidad()); // lo que entro, no lo que dice el item
        assertEquals(DEPOSITO, m.getSucursalId().longValue());
        assertEquals(TipoMovimiento.DEVOLUCION, m.getTipoMovimiento());
        verify(productoVencimientoService).delete(pv);
    }

    @Test
    void revertirCanje_sinReingresos_vuelveARetiradoSinMoverStock() {
        devolucion(DevolucionEstado.CANJEADO);
        items(item(7L, 2.0, null));
        when(productoVencimientoService.findByTipoOrigenAndOrigenId(any(), anyLong()))
                .thenReturn(Collections.emptyList());

        Devolucion res = service.revertirEstado(1L, null);

        assertEquals(DevolucionEstado.RETIRADO, res.getEstado());
        verify(movimientoStockService, never()).save(any());
    }

    @Test
    void revertirCanje_sinStockYSinPermitirNegativo_rechazaYNoMueveNada() {
        Devolucion d = devolucion(DevolucionEstado.CANJEADO);
        config.setPermitirStockNegativo(false);
        DevolucionItem item = item(5L, 2.0, 2.0);
        items(item);
        ProductoVencimiento pv = ingresoDeCanje(item, 12.0, DEPOSITO);
        when(movimientoStockService.stockByProductoIdAndSucursalId(32L, DEPOSITO)).thenReturn(4.0);

        assertThrows(GraphQLException.class, () -> service.revertirEstado(1L, null));

        assertEquals(DevolucionEstado.CANJEADO, d.getEstado());
        verify(movimientoStockService, never()).save(any());
        verify(productoVencimientoService, never()).delete(pv);
        verify(repo, never()).save(any());
    }

    @Test
    void revertirCanje_siNoSeBorraElVencimiento_rechaza() {
        Devolucion d = devolucion(DevolucionEstado.CANJEADO);
        DevolucionItem item = item(5L, 2.0, 2.0);
        items(item);
        ProductoVencimiento pv = ingresoDeCanje(item, 12.0, DEPOSITO);
        when(productoVencimientoService.delete(pv)).thenReturn(false);

        // Lanza para que la transaccion deshaga el movimiento: si no, revertir de nuevo restaria otra vez.
        assertThrows(GraphQLException.class, () -> service.revertirEstado(1L, null));

        assertEquals(DevolucionEstado.CANJEADO, d.getEstado());
        verify(repo, never()).save(any());
    }

    // ===================== reversion del separado =====================

    @Test
    void cancelarSeparado_reingresaConElTipoDeLaBaja() {
        Devolucion d = devolucion(DevolucionEstado.SEPARADO);
        d.setSucursalUbicacion(d.getSucursalOrigen());
        items(item(4L, 2.0, null));

        service.avanzarEstado(1L, DevolucionEstado.CANCELADA, null);

        MovimientoStock m = movimientos().get(0);
        assertEquals(12.0, m.getCantidad());
        assertEquals(ORIGEN, m.getSucursalId().longValue());
        assertEquals(TipoMovimiento.DEVOLUCION, m.getTipoMovimiento());
    }

    @Test
    void cancelarSeparado_sinProveedor_reingresaComoDescarte() {
        Devolucion d = devolucion(DevolucionEstado.SEPARADO);
        d.setTipo(TipoDevolucion.SIN_PROVEEDOR);
        d.setSucursalUbicacion(d.getSucursalOrigen());
        items(item(4L, 2.0, null));

        service.avanzarEstado(1L, DevolucionEstado.CANCELADA, null);

        assertEquals(TipoMovimiento.DESCARTE, movimientos().get(0).getTipoMovimiento());
    }

    // ===================== cabecera =====================

    @Test
    void actualizarCabecera_fueraDePendiente_rechaza() {
        devolucion(DevolucionEstado.SEPARADO);
        Devolucion cambios = new Devolucion();
        cambios.setId(1L);
        cambios.setSucursalOrigen(sucursal(4L));

        assertThrows(GraphQLException.class, () -> service.actualizarCabecera(cambios));

        verify(repo, never()).save(any());
    }

    @Test
    void actualizarCabecera_enPendiente_conservaElEstadoDeLaFila() {
        Devolucion d = devolucion(DevolucionEstado.PENDIENTE);
        d.setIdentificador("DEV-1-1");
        Devolucion cambios = new Devolucion();
        cambios.setId(1L);
        cambios.setTipo(TipoDevolucion.CON_PROVEEDOR);
        cambios.setProveedor(new com.franco.dev.domain.personas.Proveedor());
        cambios.setEstado(DevolucionEstado.CANJEADO); // no sale del input
        cambios.setSucursalOrigen(sucursal(4L));

        Devolucion res = service.actualizarCabecera(cambios);

        assertEquals(DevolucionEstado.PENDIENTE, res.getEstado());
        assertEquals("DEV-1-1", res.getIdentificador());
        assertEquals(4L, res.getSucursalOrigen().getId().longValue());
        assertEquals(4L, res.getSucursalUbicacion().getId().longValue()); // sigue al origen
    }

    @Test
    void crear_conProveedorSinProveedor_rechaza() {
        Devolucion nueva = new Devolucion();
        nueva.setTipo(TipoDevolucion.CON_PROVEEDOR);
        nueva.setSucursalOrigen(sucursal(ORIGEN));

        assertThrows(GraphQLException.class, () -> service.save(nueva));

        verify(repo, never()).save(any());
    }

    @Test
    void crear_sinProveedor_noPideProveedor() {
        Devolucion nueva = new Devolucion();
        nueva.setTipo(TipoDevolucion.SIN_PROVEEDOR);
        nueva.setSucursalOrigen(sucursal(ORIGEN));
        nueva.setIdentificador("DEV-1-9");

        assertEquals(DevolucionEstado.PENDIENTE, service.save(nueva).getEstado());
    }

    @Test
    void actualizarCabecera_aConProveedorSinProveedor_rechaza() {
        devolucion(DevolucionEstado.PENDIENTE);
        Devolucion cambios = new Devolucion();
        cambios.setId(1L);
        cambios.setTipo(TipoDevolucion.CON_PROVEEDOR);
        cambios.setSucursalOrigen(sucursal(ORIGEN));

        assertThrows(GraphQLException.class, () -> service.actualizarCabecera(cambios));
        verify(repo, never()).save(any());
    }

    @Test
    void actualizarCabecera_sucursalServidor_rechaza() {
        devolucion(DevolucionEstado.PENDIENTE);
        Devolucion cambios = new Devolucion();
        cambios.setId(1L);
        cambios.setSucursalOrigen(sucursal(0L));

        assertThrows(GraphQLException.class, () -> service.actualizarCabecera(cambios));
        verify(repo, never()).save(any());
    }
}
