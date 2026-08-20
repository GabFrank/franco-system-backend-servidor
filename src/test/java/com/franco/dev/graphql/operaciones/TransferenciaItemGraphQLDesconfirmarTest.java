package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoModificacion;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Des-verificar un item tiene que ser explicito.
 *
 * Antes se hacia mandando nulls por saveTransferenciaItem, que es indistinguible de "este campo no
 * viajo en el input" — la ambiguedad que borro las etapas del item 65830 de la transferencia 6290.
 */
class TransferenciaItemGraphQLDesconfirmarTest {

    private static final Long ITEM_ID = 65830L;
    private static final Long PRODUCTO_ID = 13211L;
    private static final Long SUC_ORIGEN = 1L;
    private static final Long SUC_DESTINO = 7L;

    private TransferenciaItemService service;
    private MovimientoStockService movimientoStockService;
    private TransferenciaItemGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaItemService.class);
        movimientoStockService = mock(MovimientoStockService.class);

        resolver = new TransferenciaItemGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", movimientoStockService);

        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        when(service.findById(ITEM_ID)).thenReturn(Optional.of(itemCompleto()));
    }

    private TransferenciaItem itemCompleto() {
        Producto producto = new Producto();
        producto.setId(PRODUCTO_ID);
        Presentacion presentacion = new Presentacion();
        presentacion.setProducto(producto);

        Sucursal origen = new Sucursal();
        origen.setId(SUC_ORIGEN);
        Sucursal destino = new Sucursal();
        destino.setId(SUC_DESTINO);
        Transferencia t = new Transferencia();
        t.setSucursalOrigen(origen);
        t.setSucursalDestino(destino);

        TransferenciaItem ti = new TransferenciaItem();
        ti.setId(ITEM_ID);
        ti.setTransferencia(t);
        ti.setPresentacionPreTransferencia(presentacion);
        ti.setCantidadPreTransferencia(2D);
        ti.setCantidadPreparacion(2D);
        ti.setPresentacionPreparacion(presentacion);
        ti.setCantidadTransporte(2D);
        ti.setPresentacionTransporte(presentacion);
        ti.setCantidadRecepcion(2D);
        ti.setPresentacionRecepcion(presentacion);
        ti.setMotivoModificacionRecepcion(TransferenciaItemMotivoModificacion.CANTIDAD_INCORRECTA);
        return ti;
    }

    @Test
    @DisplayName("Des-verificar recepcion limpia solo las columnas de recepcion")
    void limpiaSoloLaEtapaPedida() {
        TransferenciaItem out = resolver.desconfirmarTransferenciaItem(
                ITEM_ID, EtapaTransferencia.RECEPCION_EN_VERIFICACION);

        assertNull(out.getCantidadRecepcion(), "cantidad_recepcion debia limpiarse");
        assertNull(out.getPresentacionRecepcion(), "presentacion_recepcion debia limpiarse");
        assertNull(out.getMotivoModificacionRecepcion(), "motivo_modificacion_recepcion debia limpiarse");

        assertEquals(2D, out.getCantidadPreTransferencia(), "pre-transferencia no se toca");
        assertEquals(2D, out.getCantidadPreparacion(), "preparacion no se toca");
        assertEquals(2D, out.getCantidadTransporte(), "transporte no se toca");
        assertNotNull(out.getPresentacionPreparacion(), "presentacion_preparacion no se toca");
    }

    @Test
    @DisplayName("Des-verificar recepcion desactiva el movimiento de entrada en destino")
    void desactivaElMovimientoDeEntrada() {
        MovimientoStock entrada = new MovimientoStock();
        entrada.setEstado(true);
        when(movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(
                eq(TipoMovimiento.TRANSFERENCIA), eq(ITEM_ID), eq(SUC_DESTINO), eq(PRODUCTO_ID)))
                .thenReturn(entrada);

        resolver.desconfirmarTransferenciaItem(ITEM_ID, EtapaTransferencia.RECEPCION_EN_VERIFICACION);

        assertFalse(entrada.getEstado(), "el ingreso en destino tenia que quedar inactivo");
    }

    @Test
    @DisplayName("Des-verificar preparacion limpia preparacion y no toca recepcion")
    void limpiaPreparacion() {
        TransferenciaItem out = resolver.desconfirmarTransferenciaItem(
                ITEM_ID, EtapaTransferencia.PREPARACION_MERCADERIA);

        assertNull(out.getCantidadPreparacion(), "cantidad_preparacion debia limpiarse");
        assertEquals(2D, out.getCantidadRecepcion(), "recepcion no se toca");
    }

    @Test
    @DisplayName("No se puede des-verificar en una etapa que no verifica items")
    void etapaInvalida() {
        assertThrows(GraphQLException.class, () -> resolver.desconfirmarTransferenciaItem(
                ITEM_ID, EtapaTransferencia.TRANSPORTE_EN_CAMINO));
    }
}
