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
    private com.franco.dev.repository.financiero.CajaVirtualRepository cajaRepo;
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
        cajaRepo = mock(com.franco.dev.repository.financiero.CajaVirtualRepository.class);
        idempotenciaService = mock(IdempotenciaService.class);
        service = new PagoProveedorService(solicitudPagoService, pagoService, tesoreriaService, bancoLedgerService,
                chequeGestionService, mock(com.franco.dev.repository.financiero.ChequeraRepository.class),
                cajaRepo,
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
        when(seguridad.pagaAcotado()).thenReturn(true);
    }

    private SolicitudPago solicitud(long id, TipoSolicitudPago tipo, Long usuarioId) {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(id);
        sp.setTipo(tipo);
        sp.setEstado(SolicitudPagoEstado.SOLICITADO);
        sp.setMontoTotal(100000.0);
        sp.setMontoPagado(BigDecimal.ZERO);
        com.franco.dev.domain.financiero.Moneda gs = new com.franco.dev.domain.financiero.Moneda();
        gs.setId(1L);
        sp.setMoneda(gs);
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
    void no_paga_con_una_linea_de_ajuste() {
        // El ajuste salda deuda sin mover efectivo: con él se daría por pagada una solicitud sin pagarla.
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);

        assertTrue(rechazo(pedido(1L, FuentePago.AJUSTE)).getMessage().startsWith("No autorizado"));
        assertTrue(rechazo(pedido(1L, FuentePago.CAJA_MAYOR, FuentePago.AJUSTE)).getMessage().startsWith("No autorizado"));
        nuncaLlegaAlMotor();
    }

    @Test
    void no_paga_en_otra_moneda_ni_saldando_mas_deuda_que_el_efectivo_que_sale() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);

        List<PagoProveedorService.SolicitudConLineas> enDolares = pedido(1L, FuentePago.CAJA_MAYOR);
        enDolares.get(0).getLineas().get(0).setMonedaId(3L);
        assertTrue(rechazo(enDolares).getMessage().startsWith("No autorizado"));

        // 1 de efectivo que dice saldar 100.000 de deuda.
        List<PagoProveedorService.SolicitudConLineas> inflado = pedido(1L, FuentePago.CAJA_MAYOR);
        inflado.get(0).getLineas().get(0).setMonto(BigDecimal.ONE);
        inflado.get(0).getLineas().get(0).setMontoSolicitud(new BigDecimal("100000"));
        assertTrue(rechazo(inflado).getMessage().startsWith("No autorizado"));

        nuncaLlegaAlMotor();
    }

    @Test
    void paga_en_la_moneda_de_la_solicitud_con_o_sin_monto_convertido() {
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);
        List<PagoProveedorService.SolicitudConLineas> pedido = pedido(1L, FuentePago.CAJA_MAYOR);
        pedido.get(0).getLineas().get(0).setMonedaId(1L);
        pedido.get(0).getLineas().get(0).setMontoSolicitud(new BigDecimal("100000"));

        service.pagarLoteMixto(pedido, null, "clave");

        verify(idempotenciaService).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void con_caja_y_un_rol_de_solo_lectura_sigue_pagando_acotado() {
        // TESORERIA CAJA + TESORERIA VER: ve lo global, pero no gana el pago por banco de CPP PAGAR.
        when(seguridad.esSoloCaja()).thenReturn(false);
        solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);

        assertTrue(rechazo(pedido(1L, FuentePago.CUENTA_BANCARIA)).getMessage().startsWith("No autorizado"));
        nuncaLlegaAlMotor();
    }

    @Test
    void sin_rol_acotado_no_se_aplica_ninguna_de_estas_reglas() {
        when(seguridad.esSoloCaja()).thenReturn(false);
        when(seguridad.pagaAcotado()).thenReturn(false);
        solicitud(1L, TipoSolicitudPago.COMPRA, OTRO);

        service.pagarLoteMixto(pedido(1L, FuentePago.CUENTA_BANCARIA), null, "clave");

        verify(seguridad, never()).requireEscrituraCaja(any());
        verify(idempotenciaService).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    // ── Listar ──

    @Test
    void lista_solo_los_gastos_que_cargo_el_filtrando_en_la_consulta() {
        List<SolicitudPago> suyos = Collections.singletonList(solicitud(1L, TipoSolicitudPago.GASTO, GERENTE));
        when(spRepo.findByEstadoInAndTipoAndUsuario_Id(any(), eq(TipoSolicitudPago.GASTO), eq(GERENTE))).thenReturn(suyos);

        assertEquals(suyos, service.listarGastosPendientes());
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

    // ── Solicitudes que tesorería asignó a una caja ──

    private SolicitudPago asignada(long id, TipoSolicitudPago tipo, Long cajaId) {
        SolicitudPago sp = solicitud(id, tipo, OTRO);
        sp.setCajaVirtualAsignadaId(cajaId);
        return sp;
    }

    @Test
    void paga_una_compra_asignada_a_su_caja_desde_esa_caja() {
        asignada(1L, TipoSolicitudPago.COMPRA, CAJA);
        when(seguridad.puedeEscribirCaja(CAJA)).thenReturn(true);

        service.pagarLoteMixto(pedido(1L, FuentePago.CAJA_MAYOR), null, "clave");

        verify(idempotenciaService).ejecutar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void no_paga_una_compra_asignada_a_una_caja_que_no_puede_mover() {
        asignada(1L, TipoSolicitudPago.COMPRA, 16L);
        when(seguridad.puedeEscribirCaja(16L)).thenReturn(false);

        assertTrue(rechazo(pedido(1L, FuentePago.CAJA_MAYOR)).getMessage().startsWith("No autorizado"));
        nuncaLlegaAlMotor();
    }

    @Test
    void lo_asignado_a_una_caja_no_se_paga_desde_otra_aunque_tambien_la_pueda_mover() {
        asignada(1L, TipoSolicitudPago.COMPRA, 16L);
        when(seguridad.puedeEscribirCaja(16L)).thenReturn(true);

        // El pedido paga desde CAJA (15), pero la solicitud está asignada a la 16.
        assertTrue(rechazo(pedido(1L, FuentePago.CAJA_MAYOR)).getMessage().startsWith("No autorizado"));
        nuncaLlegaAlMotor();
    }

    @Test
    void una_obligacion_de_rrhh_no_se_paga_ni_asignada() {
        asignada(1L, TipoSolicitudPago.RRHH, CAJA);
        when(seguridad.puedeEscribirCaja(CAJA)).thenReturn(true);

        assertTrue(rechazo(pedido(1L, FuentePago.CAJA_MAYOR)).getMessage().startsWith("No autorizado"));
        nuncaLlegaAlMotor();
    }

    @Test
    void lista_solo_las_compras_asignadas_a_sus_cajas_filtrando_en_la_consulta() {
        List<SolicitudPago> suyas = Collections.singletonList(new SolicitudPago());
        when(seguridad.cajasEscribiblesIds()).thenReturn(Collections.singletonList(CAJA));
        when(spRepo.findAsignadasACajas(any(), eq(Collections.singletonList(CAJA)), eq(true))).thenReturn(suyas);

        assertEquals(suyas, service.listarPendientes(null));
        verify(spRepo, never()).findByEstadoIn(any());
    }

    @Test
    void sin_ninguna_caja_no_lista_compras_ni_consulta() {
        // Vacío es «ninguna»: jamás puede caer en el listado completo.
        when(seguridad.cajasEscribiblesIds()).thenReturn(Collections.emptyList());

        assertTrue(service.listarPendientes(null).isEmpty());
        verify(spRepo, never()).findByEstadoIn(any());
        verify(spRepo, never()).findAsignadasACajas(any(), any(), anyBoolean());
    }

    @Test
    void los_gastos_son_los_que_cargo_el_mas_los_asignados_a_sus_cajas_sin_repetir() {
        SolicitudPago propio = solicitud(1L, TipoSolicitudPago.GASTO, GERENTE);
        SolicitudPago asignado = asignada(2L, TipoSolicitudPago.GASTO, CAJA);
        when(seguridad.cajasEscribiblesIds()).thenReturn(Collections.singletonList(CAJA));
        when(spRepo.findByEstadoInAndTipoAndUsuario_Id(any(), eq(TipoSolicitudPago.GASTO), eq(GERENTE)))
                .thenReturn(Collections.singletonList(propio));
        when(spRepo.findAsignadasACajas(any(), eq(Collections.singletonList(CAJA)), eq(false)))
                .thenReturn(Arrays.asList(propio, asignado));

        assertEquals(Arrays.asList(propio, asignado), service.listarGastosPendientes());
    }

    // ── Asignar (lo hace tesorería: acá no hay modo acotado) ──

    private com.franco.dev.domain.financiero.CajaVirtual caja(long id, boolean activa) {
        com.franco.dev.domain.financiero.CajaVirtual c = new com.franco.dev.domain.financiero.CajaVirtual();
        c.setId(id);
        c.setNombre("CAJA " + id);
        c.setActivo(activa);
        when(cajaRepo.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private SolicitudPago conLock(SolicitudPago sp) {
        when(spRepo.lockById(sp.getId())).thenReturn(Optional.of(sp));
        when(solicitudPagoService.save(any())).thenAnswer(i -> i.getArgument(0));
        return sp;
    }

    @Test
    void asigna_una_compra_lista_para_pagar_y_la_desasigna() {
        SolicitudPago sp = conLock(solicitud(1L, TipoSolicitudPago.COMPRA, OTRO));
        caja(CAJA, true);

        assertEquals(CAJA, service.asignarACaja(1L, CAJA).getCajaVirtualAsignadaId());
        assertNull(service.asignarACaja(1L, null).getCajaVirtualAsignadaId());
        verify(solicitudPagoService, times(2)).save(sp);
    }

    @Test
    void no_asigna_una_obligacion_de_rrhh() {
        conLock(solicitud(1L, TipoSolicitudPago.RRHH, OTRO));
        caja(CAJA, true);

        assertThrows(GraphQLException.class, () -> service.asignarACaja(1L, CAJA));
        verify(solicitudPagoService, never()).save(any());
    }

    @Test
    void no_asigna_una_solicitud_que_no_esta_lista_para_pagar() {
        for (SolicitudPagoEstado estado : new SolicitudPagoEstado[]{SolicitudPagoEstado.PENDIENTE,
                SolicitudPagoEstado.CONCLUIDO, SolicitudPagoEstado.CANCELADO}) {
            conLock(solicitud(1L, TipoSolicitudPago.COMPRA, OTRO)).setEstado(estado);
            assertThrows(GraphQLException.class, () -> service.asignarACaja(1L, CAJA), estado.name());
        }
        verify(solicitudPagoService, never()).save(any());
    }

    @Test
    void no_asigna_a_una_caja_inactiva_o_que_no_existe() {
        conLock(solicitud(1L, TipoSolicitudPago.COMPRA, OTRO));
        caja(CAJA, false);

        assertThrows(GraphQLException.class, () -> service.asignarACaja(1L, CAJA));
        assertThrows(GraphQLException.class, () -> service.asignarACaja(1L, 999L));
        verify(solicitudPagoService, never()).save(any());
    }
}
