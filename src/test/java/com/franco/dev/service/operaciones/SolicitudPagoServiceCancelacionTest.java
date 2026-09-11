package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.financiero.CambioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Cancelar (compras) y devolver a compras (tesorería) una solicitud de pago. Quién puede hacer
 * cada cosa lo decide el resolver; acá se fijan las reglas de plata: con pagos registrados no se
 * cancela ni se devuelve, primero se anula el pago desde la caja.
 */
class SolicitudPagoServiceCancelacionTest {

    private SolicitudPagoRepository repository;
    private SolicitudPagoNotaRecepcionService notaRecepcionService;
    private SolicitudPagoService service;
    private SolicitudPago sp;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        repository = mock(SolicitudPagoRepository.class);
        notaRecepcionService = mock(SolicitudPagoNotaRecepcionService.class);
        service = new SolicitudPagoService(repository, notaRecepcionService,
                mock(NotaRecepcionRepository.class), mock(ProcesoEtapaService.class),
                mock(RecepcionMercaderiaNotaService.class), mock(RecepcionMercaderiaService.class),
                mock(MonedaRepository.class), mock(FormaPagoRepository.class), mock(CambioService.class));

        sp = new SolicitudPago();
        sp.setId(18L);
        sp.setNumeroSolicitud("SP-000018");
        sp.setTipo(TipoSolicitudPago.COMPRA);
        sp.setEstado(SolicitudPagoEstado.SOLICITADO);
        sp.setMontoTotal(8057655.0);
        sp.setMontoPagado(BigDecimal.ZERO);
        when(repository.findById(18L)).thenReturn(Optional.of(sp));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        usuario = new Usuario();
        usuario.setNickname("JPEREZ");
    }

    @Test
    void cancelarSinPagosLiberaLasNotasYGuardaElMotivo() {
        SolicitudPago r = service.cancelar(18L, "  nota mal cargada ", usuario);

        assertEquals(SolicitudPagoEstado.CANCELADO, r.getEstado());
        assertTrue(r.getObservaciones().contains("CANCELADA POR JPEREZ: NOTA MAL CARGADA"), r.getObservaciones());
        verify(notaRecepcionService).eliminarTodasRelaciones(18L);
    }

    @Test
    void cancelarConPagoParcialSeRechazaSinTocarNada() {
        sp.setEstado(SolicitudPagoEstado.PARCIAL);
        sp.setMontoPagado(new BigDecimal("3000000"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.cancelar(18L, "ya no se debe", usuario));

        assertTrue(e.getMessage().contains("anular el pago"), e.getMessage());
        assertEquals(SolicitudPagoEstado.PARCIAL, sp.getEstado());
        verify(notaRecepcionService, never()).eliminarTodasRelaciones(anyLong());
        verify(repository, never()).save(any());
    }

    /** La mutation genérica de estado (la usan los clientes viejos) pasa por la misma regla. */
    @Test
    void laMutationGenericaTampocoCancelaConPagos() {
        sp.setEstado(SolicitudPagoEstado.PARCIAL);
        sp.setMontoPagado(new BigDecimal("3000000"));

        assertThrows(IllegalStateException.class,
                () -> service.actualizarEstado(18L, SolicitudPagoEstado.CANCELADO));
        verify(notaRecepcionService, never()).eliminarTodasRelaciones(anyLong());
    }

    /** Un PARCIAL marcado a mano (pagado por fuera del sistema) tampoco se cancela, aunque no tenga monto. */
    @Test
    void unParcialSinMontoPagadoTampocoSeCancela() {
        sp.setEstado(SolicitudPagoEstado.PARCIAL);
        sp.setMontoPagado(null);

        assertThrows(IllegalStateException.class, () -> service.cancelar(18L, "x", usuario));
        assertEquals(SolicitudPagoEstado.PARCIAL, sp.getEstado());
    }

    @Test
    void cancelarSinMotivoSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> service.cancelar(18L, "   ", usuario));
        assertEquals(SolicitudPagoEstado.SOLICITADO, sp.getEstado());
    }

    /** Un gasto o un documento de RRHH se gestiona desde su módulo: cancelarlo acá dejaría el origen colgado. */
    @Test
    void noSeCancelaUnaSolicitudDeGasto() {
        sp.setTipo(TipoSolicitudPago.GASTO);

        assertThrows(IllegalStateException.class, () -> service.cancelar(18L, "no va", usuario));
        assertEquals(SolicitudPagoEstado.SOLICITADO, sp.getEstado());
    }

    /** Las solicitudes viejas no tienen tipo: son de compra. */
    @Test
    void unaSolicitudSinTipoSeTrataComoCompra() {
        sp.setTipo(null);

        assertEquals(SolicitudPagoEstado.CANCELADO, service.cancelar(18L, "x", usuario).getEstado());
    }

    @Test
    void devolverVuelveABorradorConElMotivoYSinLiberarNotas() {
        SolicitudPago r = service.devolverACompras(18L, "falta la factura", usuario);

        assertEquals(SolicitudPagoEstado.PENDIENTE, r.getEstado());
        assertTrue(r.getObservaciones().contains("DEVUELTA A COMPRAS POR JPEREZ: FALTA LA FACTURA"), r.getObservaciones());
        verify(notaRecepcionService, never()).eliminarTodasRelaciones(anyLong());
    }

    @Test
    void soloSeDevuelveUnaSolicitudEnviada() {
        sp.setEstado(SolicitudPagoEstado.PENDIENTE);

        assertThrows(IllegalStateException.class, () -> service.devolverACompras(18L, "x", usuario));
    }

    @Test
    void noSeDevuelveConPagosRegistrados() {
        sp.setMontoPagado(new BigDecimal("100"));

        assertThrows(IllegalStateException.class, () -> service.devolverACompras(18L, "x", usuario));
        assertEquals(SolicitudPagoEstado.SOLICITADO, sp.getEstado());
    }
}
