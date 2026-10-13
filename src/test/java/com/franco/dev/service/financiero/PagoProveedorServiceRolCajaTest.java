package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.financiero.enums.FuentePago;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.operaciones.PagoRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El rol acotado (TESORERIA CAJA) paga en efectivo, desde una caja que puede mover, los gastos que cargó
 * él. La misma regla vale para listar, pagar, anular y ver el detalle, y se valida antes de asentar nada.
 */
class PagoProveedorServiceRolCajaTest {

    private static final Long GERENTE = 601L;
    private static final Long OTRO = 410L;
    private static final Long CAJA = 15L;

    private SolicitudPagoService solicitudPagoService;
    private SolicitudPagoRepository spRepo;
    private PagoRepository pagoRepo;
    private TesoreriaService tesoreriaService;
    private BancoLedgerService bancoLedgerService;
    private ChequeGestionService chequeGestionService;
    private PagoSolicitudDetalleRepository detalleRepo;
    private TesoreriaSecurityService seguridad;
    private IdempotenciaService idempotenciaService;
    private PagoProveedorService service;

    @BeforeEach
    void setUp() {
        solicitudPagoService = mock(SolicitudPagoService.class);
        spRepo = mock(SolicitudPagoRepository.class);
        when(solicitudPagoService.getRepository()).thenReturn(spRepo);
        PagoService pagoService = mock(PagoService.class);
        pagoRepo = mock(PagoRepository.class);
        when(pagoService.getRepository()).thenReturn(pagoRepo);
        tesoreriaService = mock(TesoreriaService.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        chequeGestionService = mock(ChequeGestionService.class);
        detalleRepo = mock(PagoSolicitudDetalleRepository.class);
        seguridad = mock(TesoreriaSecurityService.class);
        idempotenciaService = mock(IdempotenciaService.class);
        service = new PagoProveedorService(solicitudPagoService, pagoService, tesoreriaService, bancoLedgerService,
                chequeGestionService, mock(com.franco.dev.repository.financiero.ChequeraRepository.class),
                mock(com.franco.dev.repository.financiero.CajaVirtualRepository.class),
                mock(com.franco.dev.repository.financiero.MonedaRepository.class), detalleRepo, seguridad,
                mock(com.franco.dev.repository.financiero.MovimientoBancarioRepository.class),
                mock(PreGastoService.class), mock(GastoTesoreriaService.class),
                mock(com.franco.dev.service.rrhh.ValeService.class),
                mock(com.franco.dev.service.rrhh.LiquidacionSueldoService.class),
                mock(com.franco.dev.service.rrhh.LiquidacionFinalService.class),
                mock(com.franco.dev.service.rrhh.AguinaldoService.class), idempotenciaService);

        Usuario gerente = new Usuario();
        gerente.setId(GERENTE);
        when(seguridad.currentUsuario()).thenReturn(gerente);
        when(seguridad.esSoloCaja()).thenReturn(true);
    }

    private SolicitudPago solicitud(long id, TipoSolicitudPago tipo, Long usuarioId) {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(id);
        sp.setTipo(tipo);
        sp.setEstado(SolicitudPagoEstado.SOLICITADO);
        sp.setMontoTotal(100000.0);
        sp.setMontoPagado(BigDecimal.ZERO);
        if (usuarioId != null) {
            Usuario u = new Usuario();
            u.setId(usuarioId);
            sp.setUsuario(u);
        }
        when(solicitudPagoService.findById(id)).thenReturn(Optional.of(sp));
        return sp;
    }

    private List<PagoProveedorService.SolicitudConLineas> pedido(long solicitudId, FuentePago... fuentes) {
        PagoProveedorService.SolicitudConLineas s = new PagoProveedorService.SolicitudConLineas();
        s.setSolicitudId(solicitudId);
        s.setLineas(new java.util.ArrayList<>());
        for (FuentePago f : fuentes) {
            PagoProveedorService.LineaPago l = new PagoProveedorService.LineaPago();
            l.setFuente(f);
            l.setMonto(new BigDecimal("100000"));
            if (f == FuentePago.CAJA_MAYOR) l.setCajaVirtualId(CAJA);
            if (f == FuentePago.CUENTA_BANCARIA || f == FuentePago.CHEQUE) l.setCuentaBancariaId(1L);
            s.getLineas().add(l);
        }
        return Collections.singletonList(s);
    }

    private void nuncaLlegaAlMotor() {
        verify(idempotenciaService, never()).ejecutar(any(), any(), any(), any(), any(), any(), any());
        verify(tesoreriaService, never()).registrar(any());
        verifyNoInteractions(bancoLedgerService, chequeGestionService);
    }

    private GraphQLException rechazo(List<PagoProveedorService.SolicitudConLineas> pedido) {
        return assertThrows(GraphQLException.class, () -> service.pagarLoteMixto(pedido, null, "clave"));
    }

    // ── Pagar ──

    @Test
    void paga_en_efectivo_un_gasto_que_cargo_el() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);

        service.pagarLoteMixto(pedido(1L, FuentePago.CAJA_MAYOR), null, "clave");

        verify(seguridad).requireEscrituraCaja(CAJA);
        verify(idempotenciaService).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void no_paga_un_gasto_que_cargo_otro() {
        solicitud(1L, TipoSolicitudPago.GASTO, OTRO);

        assertTrue(rechazo(pedido(1L, FuentePago.CAJA_MAYOR)).getMessage().startsWith("No autorizado"));
        nuncaLlegaAlMotor();
    }

    @Test
    void no_paga_una_solicitud_que_no_es_un_gasto_aunque_la_haya_cargado_el() {
        for (TipoSolicitudPago tipo : TipoSolicitudPago.values()) {
            if (tipo == TipoSolicitudPago.GASTO) continue;
            solicitud(1L, tipo, GERENTE);
            assertTrue(rechazo(pedido(1L, FuentePago.CAJA_MAYOR)).getMessage().startsWith("No autorizado"), tipo.name());
        }
        nuncaLlegaAlMotor();
    }

    @Test
    void no_paga_una_solicitud_que_no_existe_o_sin_usuario() {
        assertTrue(rechazo(pedido(99L, FuentePago.CAJA_MAYOR)).getMessage().startsWith("No autorizado"));
        solicitud(1L, TipoSolicitudPago.GASTO, null);
        assertTrue(rechazo(pedido(1L, FuentePago.CAJA_MAYOR)).getMessage().startsWith("No autorizado"));
        nuncaLlegaAlMotor();
    }

    @Test
    void no_paga_por_banco_ni_con_cheque() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);

        for (FuentePago fuente : new FuentePago[]{FuentePago.CUENTA_BANCARIA, FuentePago.CHEQUE}) {
            // Aunque la primera línea sea en efectivo: se recorre todo el pedido antes de asentar.
            GraphQLException e = rechazo(pedido(1L, FuentePago.CAJA_MAYOR, fuente));
            assertTrue(e.getMessage().startsWith("No autorizado"), fuente.name());
        }
        nuncaLlegaAlMotor();
    }

    @Test
    void no_paga_desde_una_caja_que_no_puede_mover() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);
        doThrow(new GraphQLException("No tenes permiso para mover plata en esta caja.")).when(seguridad).requireEscrituraCaja(CAJA);

        rechazo(pedido(1L, FuentePago.CAJA_MAYOR));
        nuncaLlegaAlMotor();
    }

    @Test
    void el_ajuste_de_redondeo_acompana_al_efectivo() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);

        service.pagarLoteMixto(pedido(1L, FuentePago.CAJA_MAYOR, FuentePago.AJUSTE), null, "clave");

        verify(idempotenciaService).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sin_rol_acotado_no_se_aplica_ninguna_de_estas_reglas() {
        when(seguridad.esSoloCaja()).thenReturn(false);
        solicitud(1L, TipoSolicitudPago.COMPRA, OTRO);

        service.pagarLoteMixto(pedido(1L, FuentePago.CUENTA_BANCARIA), null, "clave");

        verify(seguridad, never()).requireEscrituraCaja(any());
        verify(idempotenciaService).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    // ── Listar ──

    @Test
    void lista_solo_los_gastos_que_cargo_el_filtrando_en_la_consulta() {
        List<SolicitudPago> suyos = Collections.singletonList(new SolicitudPago());
        when(spRepo.findByEstadoInAndTipoAndUsuario_Id(any(), eq(TipoSolicitudPago.GASTO), eq(GERENTE))).thenReturn(suyos);

        assertSame(suyos, service.listarGastosPendientes());
        verify(spRepo, never()).findByEstadoIn(any());
    }

    @Test
    void sin_rol_acotado_lista_todos_los_gastos() {
        when(seguridad.esSoloCaja()).thenReturn(false);
        when(spRepo.findByEstadoIn(any())).thenReturn(Collections.emptyList());

        service.listarGastosPendientes();

        verify(spRepo).findByEstadoIn(any());
        verify(spRepo, never()).findByEstadoInAndTipoAndUsuario_Id(any(), any(), any());
    }

    // ── Anular ──

    private PagoSolicitudDetalle detalle(long solicitudId, Long cajaId, Long movCajaId, Long movBancoId, Long chequeId) {
        PagoSolicitudDetalle d = new PagoSolicitudDetalle();
        d.setSolicitudPagoId(solicitudId);
        d.setCajaVirtualId(cajaId);
        d.setMovimientoCajaVirtualId(movCajaId);
        d.setMovimientoBancarioId(movBancoId);
        d.setChequeId(chequeId);
        d.setMonto(new BigDecimal("100000"));
        d.setMontoSolicitud(new BigDecimal("100000"));
        return d;
    }

    private void pagoConDetalles(PagoSolicitudDetalle... detalles) {
        Pago pago = new Pago();
        pago.setId(500L);
        when(pagoRepo.lockById(500L)).thenReturn(Optional.of(pago));
        when(pagoRepo.findEstadoById(500L)).thenReturn(Optional.of(PagoEstado.CONCLUIDO));
        when(detalleRepo.findByPagoIdOrderByCreadoEnAsc(500L)).thenReturn(Arrays.asList(detalles));
    }

    private void nadaRevertido() {
        verify(tesoreriaService, never()).revertir(any(), any(), any());
        verifyNoInteractions(bancoLedgerService, chequeGestionService);
        verify(detalleRepo, never()).save(any());
    }

    @Test
    void anula_el_pago_en_efectivo_de_un_gasto_suyo() {
        SolicitudPago sp = solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);
        when(spRepo.lockById(1L)).thenReturn(Optional.of(sp));
        pagoConDetalles(detalle(1L, CAJA, 888L, null, null));
        when(tesoreriaService.findMovimiento(888L)).thenReturn(new MovimientoCajaVirtual());

        service.anularPagoCpp(500L, "x", null);

        verify(seguridad).requireEscrituraCaja(CAJA);
        verify(tesoreriaService).revertir(any(), any(), any());
    }

    @Test
    void no_anula_un_pago_con_una_parte_por_banco_o_cheque() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);

        pagoConDetalles(detalle(1L, CAJA, 888L, null, null), detalle(1L, null, null, 999L, null));
        assertTrue(assertThrows(GraphQLException.class, () -> service.anularPagoCpp(500L, "x", null))
                .getMessage().startsWith("No autorizado"));

        pagoConDetalles(detalle(1L, CAJA, 888L, null, null), detalle(1L, null, null, null, 77L));
        assertTrue(assertThrows(GraphQLException.class, () -> service.anularPagoCpp(500L, "x", null))
                .getMessage().startsWith("No autorizado"));

        nadaRevertido();
    }

    @Test
    void no_anula_un_pago_hecho_desde_su_caja_que_no_es_de_un_gasto_suyo() {
        // Un sueldo o un gasto de tesorería pagado desde la caja del gerente.
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);
        solicitud(2L, TipoSolicitudPago.RRHH, OTRO);
        pagoConDetalles(detalle(1L, CAJA, 888L, null, null), detalle(2L, CAJA, 888L, null, null));

        assertTrue(assertThrows(GraphQLException.class, () -> service.anularPagoCpp(500L, "x", null))
                .getMessage().startsWith("No autorizado"));
        nadaRevertido();
    }

    @Test
    void no_anula_un_pago_de_una_caja_que_no_puede_mover() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);
        pagoConDetalles(detalle(1L, 16L, 888L, null, null));
        doThrow(new GraphQLException("No tenes permiso para mover plata en esta caja.")).when(seguridad).requireEscrituraCaja(16L);

        assertThrows(GraphQLException.class, () -> service.anularPagoCpp(500L, "x", null));
        nadaRevertido();
    }

    // ── Detalle ──

    @Test
    void el_detalle_solo_muestra_sus_gastos_pagados_desde_una_caja_que_ve() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE).setObservaciones("FLETE");
        solicitud(2L, TipoSolicitudPago.RRHH, OTRO).setObservaciones("LIQUIDACION 2026-09 - JUAN PEREZ");
        solicitud(3L, TipoSolicitudPago.GASTO, GERENTE).setObservaciones("PAGADO POR BANCO");
        when(seguridad.cajasVisiblesIds()).thenReturn(Collections.singletonList(CAJA));
        when(detalleRepo.findByPagoIdOrderByCreadoEnAsc(500L)).thenReturn(Arrays.asList(
                detalle(1L, CAJA, 888L, null, null),
                detalle(2L, CAJA, 888L, null, null),
                detalle(3L, null, null, 999L, null)));

        List<com.franco.dev.service.financiero.dto.DetallePagoItemDto> items = service.detalleDePago(500L);

        assertEquals(1, items.size());
        assertEquals("FLETE", items.get(0).getDescripcion());
    }
}
