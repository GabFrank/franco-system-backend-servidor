package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.ValeCuotaRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Un vale con obligacion de pago (creado desde tesoreria) se paga solo desde tesoreria.
 * Caso real: bodega, vale #140 confirmado por caja dejo SP-000062 abierta para siempre.
 */
class ValeServiceConfirmarTest {

    private ValeRepository repository;
    private PagoSolicitudDetalleRepository detalleRepository;
    private MovimientoCajaVirtualService movimientoService;
    private SolicitudPagoRepository solicitudRepository;
    private ValeService service;

    @BeforeEach
    void setUp() {
        repository = mock(ValeRepository.class);
        detalleRepository = mock(PagoSolicitudDetalleRepository.class);
        movimientoService = mock(MovimientoCajaVirtualService.class);
        solicitudRepository = mock(SolicitudPagoRepository.class);
        CajaVirtualService cajaService = mock(CajaVirtualService.class);
        when(cajaService.findById(5L)).thenReturn(Optional.of(new CajaVirtual()));
        service = new ValeService(repository, cajaService, movimientoService, mock(UsuarioService.class),
                detalleRepository, mock(ValeCuotaRepository.class), mock(LiquidacionItemRepository.class),
                mock(LiquidacionFinalItemRepository.class), solicitudRepository);
        when(repository.save(any(Vale.class))).thenAnswer(i -> i.getArgument(0));
        when(movimientoService.registrarMovimiento(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            m.setId(900L);
            return m;
        });
        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(anyLong())).thenReturn(List.of());
    }

    private Vale vale(ValeEstado estado, Long solicitudPagoId) {
        Vale v = new Vale();
        v.setId(140L); v.setEstado(estado); v.setMonto(BigDecimal.valueOf(200_000));
        v.setSolicitudPagoId(solicitudPagoId);
        when(repository.findById(140L)).thenReturn(Optional.of(v));
        return v;
    }

    private PagoSolicitudDetalle detalle(boolean anulado) {
        PagoSolicitudDetalle d = new PagoSolicitudDetalle();
        d.setAnulado(anulado);
        return d;
    }

    @Test
    void confirmarRechazaUnValeConPagoPendienteEnTesoreria() {
        Vale v = vale(ValeEstado.SOLICITADO, 62L);

        GraphQLException ex = assertThrows(GraphQLException.class, () -> service.confirmar(140L, 5L, null));

        assertTrue(ex.getMessage().contains("tesoreria"));
        assertEquals(ValeEstado.SOLICITADO, v.getEstado());
        verifyNoInteractions(movimientoService);
        verify(repository, never()).save(any());
    }

    @Test
    void confirmarSinSolicitudSigueEgresandoPorCaja() {
        Vale v = vale(ValeEstado.SOLICITADO, null);

        service.confirmar(140L, 5L, null);

        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
        assertEquals(900L, v.getMovimientoCajaVirtualId());
        verify(movimientoService).registrarMovimiento(any());
    }

    @Test
    void anularRechazaSiTesoreriaTieneUnPagoVivo() {
        vale(ValeEstado.CONFIRMADO, 62L);
        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(62L))
                .thenReturn(List.of(detalle(true), detalle(false)));

        Vale v = repository.findById(140L).get();

        assertThrows(GraphQLException.class, () -> service.anular(140L));
        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
        verify(repository, never()).save(any());
        verify(movimientoService, never()).revertirMovimiento(anyLong(), anyString(), any());
    }

    @Test
    void anularConSolicitudSinPagoRevierteElEgresoDirecto() {
        // El vale #140: confirmado por caja antes del fix, solicitud sin pagos (o solo anulados).
        Vale v = vale(ValeEstado.CONFIRMADO, 62L);
        v.setCajaVirtualId(5L); v.setMovimientoCajaVirtualId(777L);
        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(62L)).thenReturn(List.of(detalle(true)));

        service.anular(140L);

        assertEquals(ValeEstado.ANULADO, v.getEstado());
        verify(movimientoService).revertirMovimiento(eq(777L), anyString(), any());
    }

    @Test
    void anularUnValePendienteCancelaSuSolicitud() {
        Vale v = vale(ValeEstado.SOLICITADO, 62L);
        SolicitudPago sp = new SolicitudPago();
        sp.setId(62L); sp.setEstado(SolicitudPagoEstado.SOLICITADO);
        when(solicitudRepository.findById(62L)).thenReturn(Optional.of(sp));

        service.anular(140L);

        assertEquals(ValeEstado.ANULADO, v.getEstado());
        assertEquals(SolicitudPagoEstado.CANCELADO, sp.getEstado());
        assertTrue(sp.getObservaciones().contains("#140"));
        verify(solicitudRepository).save(sp);
        verifyNoInteractions(movimientoService);
    }

    @Test
    void anularRechazaUnValePendienteConPagoParcialVivo() {
        vale(ValeEstado.SOLICITADO, 62L);
        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(62L)).thenReturn(List.of(detalle(false)));

        assertThrows(GraphQLException.class, () -> service.anular(140L));
        verify(solicitudRepository, never()).save(any());
    }
}
