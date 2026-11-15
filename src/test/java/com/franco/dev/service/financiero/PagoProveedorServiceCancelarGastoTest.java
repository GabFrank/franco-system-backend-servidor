package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.PagoRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaNotaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.operaciones.SolicitudPagoNotaRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tesorería cancela un gasto que ya no corresponde (#599). Las reglas de estado y motivo son las del
 * SolicitudPagoService real; el resto son mocks. Y la otra punta: anular un pago no reabre una solicitud
 * cancelada.
 */
class PagoProveedorServiceCancelarGastoTest {

    private SolicitudPagoRepository spRepo;
    private PagoRepository pagoRepo;
    private PagoSolicitudDetalleRepository detalleRepo;
    private TesoreriaSecurityService seguridad;
    private TesoreriaService tesoreriaService;
    private PreGastoService preGastoService;
    private GastoTesoreriaService gastoTesoreriaService;
    private PagoProveedorService service;
    private SolicitudPago sp;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        spRepo = mock(SolicitudPagoRepository.class);
        when(spRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        SolicitudPagoService solicitudPagoService = new SolicitudPagoService(spRepo,
                mock(SolicitudPagoNotaRecepcionService.class), mock(NotaRecepcionRepository.class),
                mock(ProcesoEtapaService.class), mock(RecepcionMercaderiaNotaService.class),
                mock(RecepcionMercaderiaService.class), mock(MonedaRepository.class),
                mock(FormaPagoRepository.class), mock(CambioService.class));
        PagoService pagoService = mock(PagoService.class);
        pagoRepo = mock(PagoRepository.class);
        when(pagoService.getRepository()).thenReturn(pagoRepo);
        detalleRepo = mock(PagoSolicitudDetalleRepository.class);
        seguridad = mock(TesoreriaSecurityService.class);
        tesoreriaService = mock(TesoreriaService.class);
        preGastoService = mock(PreGastoService.class);
        gastoTesoreriaService = mock(GastoTesoreriaService.class);
        service = new PagoProveedorService(solicitudPagoService, pagoService, tesoreriaService,
                mock(BancoLedgerService.class), mock(ChequeGestionService.class),
                mock(com.franco.dev.repository.financiero.ChequeraRepository.class),
                mock(com.franco.dev.repository.financiero.CajaVirtualRepository.class),
                mock(com.franco.dev.repository.financiero.MonedaRepository.class), detalleRepo, seguridad,
                mock(com.franco.dev.repository.financiero.MovimientoBancarioRepository.class),
                preGastoService, gastoTesoreriaService,
                mock(com.franco.dev.service.rrhh.ValeService.class),
                mock(com.franco.dev.service.rrhh.LiquidacionSueldoService.class),
                mock(com.franco.dev.service.rrhh.LiquidacionFinalService.class),
                mock(com.franco.dev.service.rrhh.AguinaldoService.class), mock(IdempotenciaService.class));

        sp = new SolicitudPago();
        sp.setId(30L);
        sp.setNumeroSolicitud("SP-000030");
        sp.setTipo(TipoSolicitudPago.GASTO);
        sp.setEstado(SolicitudPagoEstado.SOLICITADO);
        sp.setMontoTotal(2500000.0);
        sp.setMontoPagado(BigDecimal.ZERO);
        sp.setObservaciones("ALQUILER NOVIEMBRE");
        when(spRepo.lockById(30L)).thenReturn(Optional.of(sp));
        when(spRepo.findById(30L)).thenReturn(Optional.of(sp));
        when(detalleRepo.findBySolicitudPagoIdOrderByCreadoEnAsc(30L)).thenReturn(Collections.emptyList());

        usuario = new Usuario();
        usuario.setNickname("JPEREZ");
    }

    private void nadaCambio(SolicitudPagoEstado estado) {
        assertEquals(estado, sp.getEstado());
        verify(spRepo, never()).save(any());
        verifyNoInteractions(gastoTesoreriaService, preGastoService);
    }

    private String rechazo() {
        return assertThrows(RuntimeException.class, () -> service.cancelarGasto(30L, "ya se pagó con otra", usuario))
                .getMessage();
    }

    @Test
    void cancelaUnGastoSinPagosYDejaElMontoComoRastro() {
        SolicitudPago r = service.cancelarGasto(30L, "  se pagó con otra solicitud ", usuario);

        assertEquals(SolicitudPagoEstado.CANCELADO, r.getEstado());
        String texto = "CANCELADA POR JPEREZ (estaba SOLICITADO): SE PAGÓ CON OTRA SOLICITUD";
        assertEquals("ALQUILER NOVIEMBRE | " + texto, r.getObservaciones());
        assertEquals(2500000.0, r.getMontoTotal());
        verify(gastoTesoreriaService).sincronizarDesdeSolicitudPago(sp);
        // El PreGasto recibe solo el texto de esta cancelación, no todas las observaciones.
        verify(preGastoService).cancelarDesdeSolicitudPago(sp, texto, usuario);
    }

    @Test
    void conPagoParcialONetoPagadoNoCancela() {
        sp.setEstado(SolicitudPagoEstado.PARCIAL);
        assertTrue(rechazo().contains("anular el pago"));
        nadaCambio(SolicitudPagoEstado.PARCIAL);

        sp.setEstado(SolicitudPagoEstado.SOLICITADO);
        sp.setMontoPagado(new BigDecimal("1000"));
        assertTrue(rechazo().contains("anular el pago"));
        nadaCambio(SolicitudPagoEstado.SOLICITADO);
    }

    @Test
    void conUnDetalleDePagoVivoNoCancelaAunqueElMontoDigaCero() {
        PagoSolicitudDetalle vivo = new PagoSolicitudDetalle();
        vivo.setAnulado(false);
        PagoSolicitudDetalle anulado = new PagoSolicitudDetalle();
        anulado.setAnulado(true);
        when(detalleRepo.findBySolicitudPagoIdOrderByCreadoEnAsc(30L)).thenReturn(java.util.Arrays.asList(anulado, vivo));

        assertTrue(rechazo().contains("pago sin anular"));
        nadaCambio(SolicitudPagoEstado.SOLICITADO);
    }

    @Test
    void conTodosLosDetallesAnuladosSiCancela() {
        PagoSolicitudDetalle anulado = new PagoSolicitudDetalle();
        anulado.setAnulado(true);
        when(detalleRepo.findBySolicitudPagoIdOrderByCreadoEnAsc(30L)).thenReturn(Collections.singletonList(anulado));

        assertEquals(SolicitudPagoEstado.CANCELADO, service.cancelarGasto(30L, "duplicado", usuario).getEstado());
    }

    @Test
    void soloCancelaGastos() {
        sp.setTipo(TipoSolicitudPago.COMPRA);
        assertTrue(rechazo().contains("no es un gasto"));
        sp.setTipo(TipoSolicitudPago.RRHH);
        assertTrue(rechazo().contains("no es un gasto"));
        nadaCambio(SolicitudPagoEstado.SOLICITADO);
    }

    @Test
    void sinMotivoNoCancela() {
        assertThrows(IllegalArgumentException.class, () -> service.cancelarGasto(30L, "   ", usuario));
        assertThrows(IllegalArgumentException.class, () -> service.cancelarGasto(30L, null, usuario));
        nadaCambio(SolicitudPagoEstado.SOLICITADO);
    }

    @Test
    void conCajaAsignadaPideEscrituraSobreEsaCaja() {
        sp.setCajaVirtualAsignadaId(15L);
        doThrow(new GraphQLException("No tenes permiso para mover plata en esta caja."))
                .when(seguridad).requireEscrituraCaja(15L);

        assertTrue(rechazo().startsWith("No tenes permiso"));
        nadaCambio(SolicitudPagoEstado.SOLICITADO);
    }

    @Test
    void sinCajaAsignadaNoPideAcceso() {
        service.cancelarGasto(30L, "duplicado", usuario);
        verify(seguridad, never()).requireEscrituraCaja(any());
    }

    @Test
    void unGastoConcluidoOYaCanceladoNoSeCancela() {
        sp.setEstado(SolicitudPagoEstado.CONCLUIDO);
        assertTrue(rechazo().contains("no se puede cancelar"));
        nadaCambio(SolicitudPagoEstado.CONCLUIDO);

        sp.setEstado(SolicitudPagoEstado.CANCELADO);
        assertTrue(rechazo().contains("ya está cancelado"));
        nadaCambio(SolicitudPagoEstado.CANCELADO);
    }

    @Test
    void anularUnPagoDeUnaSolicitudCanceladaNoLaReabre() {
        sp.setEstado(SolicitudPagoEstado.CANCELADO);
        Pago pago = new Pago();
        pago.setId(500L);
        pago.setEstado(PagoEstado.CONCLUIDO);
        pago.setCreadoEn(LocalDateTime.now());
        when(pagoRepo.lockById(500L)).thenReturn(Optional.of(pago));
        when(pagoRepo.findEstadoById(500L)).thenReturn(Optional.of(PagoEstado.CONCLUIDO));
        PagoSolicitudDetalle d = new PagoSolicitudDetalle();
        d.setSolicitudPagoId(30L);
        d.setMovimientoCajaVirtualId(888L);
        d.setCajaVirtualId(15L);
        d.setMonto(new BigDecimal("2500000"));
        d.setAnulado(false);
        when(detalleRepo.findByPagoIdOrderByCreadoEnAsc(500L)).thenReturn(Collections.singletonList(d));

        String msg = assertThrows(GraphQLException.class, () -> service.anularPagoCpp(500L, "x", usuario)).getMessage();

        assertTrue(msg.contains("SP-000030") && msg.contains("cancelada"), msg);
        assertEquals(SolicitudPagoEstado.CANCELADO, sp.getEstado());
        verify(tesoreriaService, never()).revertir(any(), anyString(), any());
        verify(detalleRepo, never()).save(any());
        verify(spRepo, never()).save(any());
        verifyNoInteractions(preGastoService, gastoTesoreriaService);
    }
}
