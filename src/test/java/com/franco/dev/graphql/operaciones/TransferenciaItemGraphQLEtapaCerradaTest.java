package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemLoteService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #468: un save de item que llega con la pantalla vieja despues de un avance de la misma
 * transferencia fallaba con un 40001 crudo, o pisaba en silencio lo que el avance acababa de copiar.
 * Ahora espera con la transferencia y el item bloqueados y rechaza cambiar etapas ya cerradas.
 */
class TransferenciaItemGraphQLEtapaCerradaTest {

    private static final Long ITEM_ID = 7001L;
    private static final Long TRANSFERENCIA_ID = 900L;
    private static final Long PRESENTACION_ID = 10L;
    private static final LocalDateTime VENCIMIENTO = LocalDateTime.of(2026, 10, 4, 0, 0);

    private TransferenciaItemService service;
    private TransferenciaService transferenciaService;
    private MovimientoStockService movimientoStockService;
    private CostosPorProductoService costosPorProductoService;
    private PlatformTransactionManager transactionManager;
    private TransferenciaItemGraphQL resolver;
    private Transferencia transferencia;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaItemService.class);
        transferenciaService = mock(TransferenciaService.class);
        movimientoStockService = mock(MovimientoStockService.class);
        costosPorProductoService = mock(CostosPorProductoService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        PresentacionService presentacionService = mock(PresentacionService.class);

        resolver = new TransferenciaItemGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "seg", new com.franco.dev.service.financiero.TesoreriaSecurityFalso(1L, "VER TRANSFERENCIA"));
        ReflectionTestUtils.setField(resolver, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(resolver, "transferenciaService", transferenciaService);
        ReflectionTestUtils.setField(resolver, "transferenciaItemLoteService", mock(TransferenciaItemLoteService.class));
        ReflectionTestUtils.setField(resolver, "presentacionService", presentacionService);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", movimientoStockService);
        ReflectionTestUtils.setField(resolver, "controlStockNegativoService", mock(ControlStockNegativoService.class));
        ReflectionTestUtils.setField(resolver, "costosPorProductoService", costosPorProductoService);

        transferencia = new Transferencia();
        transferencia.setId(TRANSFERENCIA_ID);

        when(usuarioService.findById(55L)).thenReturn(Optional.of(new Usuario()));
        when(presentacionService.findById(anyLong())).thenAnswer(i -> Optional.of(presentacion(i.getArgument(0))));
        when(service.transferenciaIdDe(ITEM_ID)).thenReturn(TRANSFERENCIA_ID);
        when(transferenciaService.lockCompartidoById(TRANSFERENCIA_ID)).thenReturn(Optional.of(transferencia));
        when(service.lockById(ITEM_ID)).thenReturn(Optional.of(itemGuardado()));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Presentacion presentacion(Long id) {
        Presentacion p = new Presentacion();
        p.setId(id);
        return p;
    }

    /** Item con las cuatro etapas verificadas: 3 unidades, misma presentacion y vencimiento. */
    private TransferenciaItem itemGuardado() {
        TransferenciaItem ti = new TransferenciaItem();
        ti.setId(ITEM_ID);
        ti.setTransferencia(transferencia);
        ti.setCantidadPreTransferencia(3D);
        ti.setCantidadPreparacion(3D);
        ti.setCantidadTransporte(3D);
        ti.setCantidadRecepcion(3D);
        ti.setPresentacionPreTransferencia(presentacion(PRESENTACION_ID));
        ti.setPresentacionPreparacion(presentacion(PRESENTACION_ID));
        ti.setPresentacionTransporte(presentacion(PRESENTACION_ID));
        ti.setPresentacionRecepcion(presentacion(PRESENTACION_ID));
        ti.setVencimientoPreTransferencia(VENCIMIENTO);
        ti.setVencimientoPreparacion(VENCIMIENTO);
        ti.setVencimientoTransporte(VENCIMIENTO);
        ti.setVencimientoRecepcion(VENCIMIENTO);
        return ti;
    }

    private static TransferenciaItemInput base(Long id) {
        TransferenciaItemInput in = new TransferenciaItemInput();
        in.setId(id);
        in.setTransferenciaId(TRANSFERENCIA_ID);
        in.setUsuarioId(55L);
        return in;
    }

    /**
     * Lo que manda el desktop con toInput(): el item entero con lo guardado. El vencimiento llega
     * una hora corrido (00:00 del dia del cambio de horario no existe en hora local).
     */
    private static TransferenciaItemInput reenvioEntero() {
        TransferenciaItemInput in = base(ITEM_ID);
        in.setCantidadPreTransferencia(3D);
        in.setCantidadPreparacion(3D);
        in.setCantidadTransporte(3D);
        in.setCantidadRecepcion(3D);
        in.setPresentacionPreTransferenciaId(PRESENTACION_ID);
        in.setPresentacionPreparacionId(PRESENTACION_ID);
        in.setPresentacionTransporteId(PRESENTACION_ID);
        in.setPresentacionRecepcionId(PRESENTACION_ID);
        in.setVencimientoPreTransferencia("2026-10-04 01:00");
        in.setVencimientoPreparacion("2026-10-04 01:00");
        in.setVencimientoTransporte("2026-10-04 01:00");
        in.setVencimientoRecepcion("2026-10-04 01:00");
        return in;
    }

    private void enEtapa(EtapaTransferencia etapa) {
        transferencia.setEtapa(etapa);
    }

    private void rechaza(TransferenciaItemInput in) {
        GraphQLException ex = assertThrows(GraphQLException.class, () -> resolver.saveTransferenciaItem(in, null));
        assertTrue(ex.getMessage().contains("Volve a abrirla"), ex.getMessage());
        verify(service, never()).save(any());
        verify(movimientoStockService, never()).createMovimientoFromTransferenciaItem(any());
    }

    @Test
    @DisplayName("bloquea la transferencia del item (no la del input) y despues el item, dentro de una transaccion")
    void bloqueaTransferenciaYDespuesItem() {
        enEtapa(EtapaTransferencia.PREPARACION_MERCADERIA);
        TransferenciaItemInput in = base(ITEM_ID);
        in.setTransferenciaId(999L);

        resolver.saveTransferenciaItem(in, null);

        InOrder orden = inOrder(transactionManager, transferenciaService, service);
        orden.verify(transactionManager).getTransaction(any());
        orden.verify(transferenciaService).lockCompartidoById(TRANSFERENCIA_ID);
        orden.verify(service).lockById(ITEM_ID);
        orden.verify(service).save(any());
        orden.verify(transactionManager).commit(any());
        verify(transferenciaService, never()).lockCompartidoById(999L);
        verify(service, never()).findById(any());
    }

    @Test
    @DisplayName("preparacion concluida: cambiar la cantidad preparada se rechaza sin guardar ni mover stock")
    void cambiarPreparacionConcluidaSeRechaza() {
        enEtapa(EtapaTransferencia.PREPARACION_MERCADERIA_CONCLUIDA);
        TransferenciaItemInput in = reenvioEntero();
        in.setCantidadPreparacion(5D);

        rechaza(in);
    }

    @Test
    @DisplayName("en preparacion: cambiar preparacion pasa, cambiar pre-transferencia se rechaza")
    void enPreparacionSoloSeCambiaPreparacion() {
        enEtapa(EtapaTransferencia.PREPARACION_MERCADERIA);
        TransferenciaItemInput preparacion = reenvioEntero();
        preparacion.setCantidadPreparacion(2D);
        preparacion.setPresentacionPreparacionId(11L);
        assertDoesNotThrow(() -> resolver.saveTransferenciaItem(preparacion, null));

        TransferenciaItemInput preTransferencia = reenvioEntero();
        preTransferencia.setCantidadPreTransferencia(5D);
        assertThrows(GraphQLException.class, () -> resolver.saveTransferenciaItem(preTransferencia, null));
    }

    @Test
    @DisplayName("presentacion y vencimiento de una etapa cerrada tambien cuentan como cambio")
    void presentacionYVencimientoCuentan() {
        enEtapa(EtapaTransferencia.RECEPCION_CONCLUIDA);
        TransferenciaItemInput presentacion = reenvioEntero();
        presentacion.setPresentacionRecepcionId(11L);
        rechaza(presentacion);

        TransferenciaItemInput vencimiento = reenvioEntero();
        vencimiento.setVencimientoTransporte("2026-10-05 00:00");
        rechaza(vencimiento);
    }

    @ParameterizedTest
    @EnumSource(EtapaTransferencia.class)
    @DisplayName("el reenvio del item entero con lo guardado pasa en todas las etapas")
    void reenvioEnteroPasa(EtapaTransferencia etapa) {
        enEtapa(etapa);
        assertDoesNotThrow(() -> resolver.saveTransferenciaItem(reenvioEntero(), null));
        verify(service).save(any());
    }

    @Test
    @DisplayName("etapas intermedias: en camino ya no se cambia transporte, todavia se cambia recepcion")
    void etapasIntermedias() {
        enEtapa(EtapaTransferencia.TRANSPORTE_EN_CAMINO);
        TransferenciaItemInput transporte = reenvioEntero();
        transporte.setCantidadTransporte(1D);
        assertThrows(GraphQLException.class, () -> resolver.saveTransferenciaItem(transporte, null));

        enEtapa(EtapaTransferencia.TRANSPORTE_EN_DESTINO);
        TransferenciaItemInput recepcion = reenvioEntero();
        recepcion.setCantidadRecepcion(1D);
        assertDoesNotThrow(() -> resolver.saveTransferenciaItem(recepcion, null));
    }

    @Test
    @DisplayName("una transferencia sin etapa no valida")
    void sinEtapaNoValida() {
        enEtapa(null);
        TransferenciaItemInput in = reenvioEntero();
        in.setCantidadPreTransferencia(9D);
        assertDoesNotThrow(() -> resolver.saveTransferenciaItem(in, null));
    }

    @Test
    @DisplayName("un item nuevo con ceros en las etapas siguientes (frc-mobile) pasa")
    void itemNuevoConCerosPasa() {
        enEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);
        TransferenciaItemInput in = base(null);
        in.setCantidadPreTransferencia(3D);
        in.setPresentacionPreTransferenciaId(PRESENTACION_ID);
        in.setCantidadPreparacion(0D);
        in.setCantidadTransporte(0D);
        in.setCantidadRecepcion(0D);

        assertDoesNotThrow(() -> resolver.saveTransferenciaItem(in, null));
        verify(service, never()).lockById(any());
    }

    @Test
    @DisplayName("el costo de COMPRAS se registra despues del commit")
    void costoDeComprasDespuesDelCommit() {
        enEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);
        Sucursal compras = new Sucursal();
        compras.setNombre(CostosPorProductoService.SUCURSAL_COMPRAS);
        transferencia.setSucursalOrigen(compras);
        TransferenciaItemInput in = base(null);
        in.setCantidadPreTransferencia(3D);
        in.setPresentacionPreTransferenciaId(PRESENTACION_ID);
        Presentacion conProducto = presentacion(PRESENTACION_ID);
        conProducto.setProducto(new Producto());
        PresentacionService presentacionService = mock(PresentacionService.class);
        when(presentacionService.findById(PRESENTACION_ID)).thenReturn(Optional.of(conProducto));
        ReflectionTestUtils.setField(resolver, "presentacionService", presentacionService);

        resolver.saveTransferenciaItem(in, 1500D);

        InOrder orden = inOrder(transactionManager, costosPorProductoService);
        orden.verify(transactionManager).commit(any());
        orden.verify(costosPorProductoService).registrarCostoCompraManual(any(), any(), any(), any(), any(), any());
    }
}
