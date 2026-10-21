package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoRechazo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rechazar un ítem des-verificado (sin cantidad ni presentación en la etapa) no tiene que reventar
 * al generar su movimiento, y ese movimiento queda inactivo (#474).
 */
class MovimientoStockServiceTransferenciaTest {

    private static final Long ITEM_ID = 816910L;
    private static final Long PRODUCTO_ID = 13211L;
    private static final Long SUC_ORIGEN = 13L;
    private static final Long SUC_DESTINO = 7L;

    private MovimientoStockRepository repository;
    private MovimientoStockLoteService movimientoStockLoteService;
    private MovimientoStockService service;
    private Producto producto;

    @BeforeEach
    void setUp() {
        repository = mock(MovimientoStockRepository.class);
        movimientoStockLoteService = mock(MovimientoStockLoteService.class);
        service = new MovimientoStockService(
                repository,
                mock(ModificacionService.class),
                mock(SucursalService.class),
                movimientoStockLoteService,
                mock(LoteFefoService.class),
                mock(TransferenciaItemLoteService.class));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.siguienteId()).thenReturn(1L);
        producto = new Producto();
        producto.setId(PRODUCTO_ID);
        producto.setLote(false);
        movimientoEn(SUC_ORIGEN, null);
        movimientoEn(SUC_DESTINO, null);
    }

    private void movimientoEn(Long sucursalId, MovimientoStock ms) {
        List<MovimientoStock> res = ms == null ? Collections.emptyList() : Collections.singletonList(ms);
        when(repository.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(
                TipoMovimiento.TRANSFERENCIA, ITEM_ID, sucursalId, PRODUCTO_ID)).thenReturn(res);
    }

    private MovimientoStock movimiento(Long sucursalId, double cantidad) {
        MovimientoStock ms = new MovimientoStock();
        ms.setId(50L);
        ms.setSucursalId(sucursalId);
        ms.setCantidad(cantidad);
        ms.setEstado(true);
        ms.setProducto(producto);
        return ms;
    }

    private Sucursal sucursal(Long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        return s;
    }

    private Presentacion presentacion(double cantidad) {
        Presentacion p = new Presentacion();
        p.setCantidad(cantidad);
        p.setProducto(producto);
        return p;
    }

    /** Un ítem en la etapa dada, des-verificado: sin cantidad ni presentación de ninguna etapa posterior. */
    private TransferenciaItem item(EtapaTransferencia etapa) {
        Transferencia t = new Transferencia();
        t.setEtapa(etapa);
        t.setEstado(TransferenciaEstado.EN_ORIGEN);
        t.setSucursalOrigen(sucursal(SUC_ORIGEN));
        t.setSucursalDestino(sucursal(SUC_DESTINO));
        TransferenciaItem ti = new TransferenciaItem();
        ti.setId(ITEM_ID);
        ti.setTransferencia(t);
        ti.setPresentacionPreTransferencia(presentacion(1));
        ti.setCantidadPreTransferencia(10.0);
        return ti;
    }

    @Test
    void preparacionRechazadaSinCantidadDesactivaLaSalida() {
        MovimientoStock salida = movimiento(SUC_ORIGEN, -10);
        movimientoEn(SUC_ORIGEN, salida);
        TransferenciaItem ti = item(EtapaTransferencia.PREPARACION_MERCADERIA);
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);

        List<MovimientoStock> res = service.createMovimientoFromTransferenciaItem(ti);

        assertFalse(res.get(0).getEstado());
        assertEquals(-10, res.get(0).getCantidad());
        verify(repository).save(salida);
    }

    @Test
    void preparacionRechazadaSinCantidadNiSalidaNoCreaMovimiento() {
        TransferenciaItem ti = item(EtapaTransferencia.PREPARACION_MERCADERIA);
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);

        List<MovimientoStock> res = service.createMovimientoFromTransferenciaItem(ti);

        assertNull(res.get(0));
        verify(repository, never()).save(any());
    }

    @Test
    void preparacionConCantidadGeneraLaSalidaDeSiempre() {
        TransferenciaItem ti = item(EtapaTransferencia.PREPARACION_MERCADERIA);
        ti.setCantidadPreparacion(4.0);
        ti.setPresentacionPreparacion(presentacion(6));

        MovimientoStock salida = service.createMovimientoFromTransferenciaItem(ti).get(0);

        assertTrue(salida.getEstado());
        assertEquals(-24, salida.getCantidad());
        assertEquals(SUC_ORIGEN, salida.getSucursalId());
    }

    @Test
    void transporteRechazadoSinCantidadDesactivaLaSalida() {
        MovimientoStock salida = movimiento(SUC_ORIGEN, -10);
        movimientoEn(SUC_ORIGEN, salida);
        TransferenciaItem ti = item(EtapaTransferencia.TRANSPORTE_VERIFICACION);
        ti.setMotivoRechazoTransporte(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);

        MovimientoStock res = service.createMovimientoFromTransferenciaItem(ti).get(0);

        assertFalse(res.getEstado());
        assertEquals(-10, res.getCantidad());
        verify(repository).save(salida);
    }

    @Test
    void transporteSinSalidaNiCantidadNoHaceNada() {
        TransferenciaItem ti = item(EtapaTransferencia.TRANSPORTE_VERIFICACION);

        List<MovimientoStock> res = service.createMovimientoFromTransferenciaItem(ti);

        assertNull(res.get(0));
        verify(repository, never()).save(any());
    }

    @Test
    void recepcionRechazadaSinCantidadNoCreaEntradaYDesactivaLaSalida() {
        MovimientoStock salida = movimiento(SUC_ORIGEN, -10);
        movimientoEn(SUC_ORIGEN, salida);
        TransferenciaItem ti = item(EtapaTransferencia.RECEPCION_EN_VERIFICACION);
        ti.setMotivoRechazoRecepcion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);

        List<MovimientoStock> res = service.createMovimientoFromTransferenciaItem(ti);

        assertFalse(res.get(0).getEstado());
        assertNull(res.get(1));
        verify(repository).save(salida);
    }

    @Test
    void recepcionConCantidadSinSalidaCreaLaEntrada() {
        TransferenciaItem ti = item(EtapaTransferencia.RECEPCION_EN_VERIFICACION);
        ti.setCantidadRecepcion(3.0);
        ti.setPresentacionRecepcion(presentacion(2));

        List<MovimientoStock> res = service.createMovimientoFromTransferenciaItem(ti);

        assertNull(res.get(0));
        assertTrue(res.get(1).getEstado());
        assertEquals(6, res.get(1).getCantidad());
        assertEquals(SUC_DESTINO, res.get(1).getSucursalId());
    }

    @Test
    void preparacionSinCantidadNiRechazoCortaConMensaje() {
        MovimientoStock salida = movimiento(SUC_ORIGEN, -10);
        movimientoEn(SUC_ORIGEN, salida);
        TransferenciaItem ti = item(EtapaTransferencia.PREPARACION_MERCADERIA);

        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.createMovimientoFromTransferenciaItem(ti));

        assertTrue(ex.getMessage().contains(ITEM_ID.toString()));
        verify(repository, never()).save(any());
    }

    @Test
    void recepcionSinCantidadNiRechazoCortaConMensaje() {
        // Es el avance a recepción con un ítem que quedó sin verificar en transporte.
        movimientoEn(SUC_ORIGEN, movimiento(SUC_ORIGEN, -10));
        TransferenciaItem ti = item(EtapaTransferencia.RECEPCION_EN_VERIFICACION);

        assertThrows(GraphQLException.class, () -> service.createMovimientoFromTransferenciaItem(ti));
        verify(repository, never()).save(any());
    }

    @Test
    void volverACargarCantidadDespuesDelRechazoReactivaLaSalida() {
        MovimientoStock salida = movimiento(SUC_ORIGEN, -10);
        salida.setEstado(false);
        movimientoEn(SUC_ORIGEN, salida);
        TransferenciaItem ti = item(EtapaTransferencia.PREPARACION_MERCADERIA);
        ti.setCantidadPreparacion(2.0);
        ti.setPresentacionPreparacion(presentacion(5));

        MovimientoStock res = service.createMovimientoFromTransferenciaItem(ti).get(0);

        assertTrue(res.getEstado());
        assertEquals(-10, res.getCantidad());
        assertEquals(50L, res.getId());
        verify(repository).save(salida);
    }

    @Test
    void productoConLoteRechazadoSinCantidadDesactivaElDesglose() {
        producto.setLote(true);
        MovimientoStock salida = movimiento(SUC_ORIGEN, -10);
        movimientoEn(SUC_ORIGEN, salida);
        TransferenciaItem ti = item(EtapaTransferencia.PREPARACION_MERCADERIA);
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);

        service.createMovimientoFromTransferenciaItem(ti);

        assertFalse(salida.getEstado());
        verify(movimientoStockLoteService).sincronizarEstado(salida);
    }
}
