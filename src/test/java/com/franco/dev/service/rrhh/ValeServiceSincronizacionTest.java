package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
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
import static org.mockito.Mockito.*;

/**
 * Sincronización vale ↔ obligación de pago: es lo que decide si la liquidación va a poder
 * descontar el vale del sueldo (descuenta los CONFIRMADO).
 */
class ValeServiceSincronizacionTest {

    private ValeRepository repository;
    private PagoSolicitudDetalleRepository detalleRepository;
    private ValeService service;

    @BeforeEach
    void setUp() {
        repository = mock(ValeRepository.class);
        detalleRepository = mock(PagoSolicitudDetalleRepository.class);
        service = new ValeService(repository, mock(CajaVirtualService.class),
                mock(MovimientoCajaVirtualService.class), mock(UsuarioService.class), detalleRepository);
        when(repository.save(any(Vale.class))).thenAnswer(i -> i.getArgument(0));
        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(anyLong())).thenReturn(List.of());
    }

    private SolicitudPago solicitud(SolicitudPagoEstado estado) {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(88L); sp.setTipo(TipoSolicitudPago.RRHH); sp.setEstado(estado);
        return sp;
    }

    private Vale vale(ValeEstado estado) {
        Vale v = new Vale();
        v.setId(1L); v.setEstado(estado); v.setMonto(BigDecimal.valueOf(1_000_000));
        v.setSolicitudPagoId(88L);
        return v;
    }

    @Test
    void solicitudConcluidaDejaElValeConfirmadoYLinkeaElMovimientoDeCaja() {
        Vale v = vale(ValeEstado.SOLICITADO);
        when(repository.findBySolicitudPagoId(88L)).thenReturn(v);
        PagoSolicitudDetalle d = new PagoSolicitudDetalle();
        d.setAnulado(false); d.setMovimientoCajaVirtualId(555L); d.setCajaVirtualId(3L);
        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(88L)).thenReturn(List.of(d));

        service.sincronizarDesdeSolicitudPago(solicitud(SolicitudPagoEstado.CONCLUIDO));

        // CONFIRMADO es lo que mira la liquidación para descontarlo del sueldo.
        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
        assertEquals(555L, v.getMovimientoCajaVirtualId());
        assertEquals(3L, v.getCajaVirtualId());
    }

    @Test
    void anularElPagoDevuelveElValeAPendiente() {
        Vale v = vale(ValeEstado.CONFIRMADO);
        v.setMovimientoCajaVirtualId(555L); v.setCajaVirtualId(3L);
        when(repository.findBySolicitudPagoId(88L)).thenReturn(v);

        service.sincronizarDesdeSolicitudPago(solicitud(SolicitudPagoEstado.SOLICITADO));

        assertEquals(ValeEstado.SOLICITADO, v.getEstado());
        assertNull(v.getMovimientoCajaVirtualId());
        assertNull(v.getCajaVirtualId());
    }

    @Test
    void noTocaUnValeYaDescontadoEnLiquidacion() {
        Vale v = vale(ValeEstado.DESCONTADO);
        when(repository.findBySolicitudPagoId(88L)).thenReturn(v);

        service.sincronizarDesdeSolicitudPago(solicitud(SolicitudPagoEstado.SOLICITADO));

        assertEquals(ValeEstado.DESCONTADO, v.getEstado());
        verify(repository, never()).save(any(Vale.class));
    }

    @Test
    void ignoraSolicitudesQueNoSonDeVale() {
        SolicitudPago sp = solicitud(SolicitudPagoEstado.CONCLUIDO);
        sp.setTipo(TipoSolicitudPago.GASTO);

        service.sincronizarDesdeSolicitudPago(sp);

        verify(repository, never()).findBySolicitudPagoId(anyLong());
    }

    @Test
    void noSePuedeAnularPorRrhhUnValePagadoDesdeTesoreria() {
        // Anularlo acá no devolvería la plata: el egreso salió por un evento de pago consolidado.
        Vale v = vale(ValeEstado.CONFIRMADO);
        when(repository.findById(1L)).thenReturn(Optional.of(v));

        GraphQLException ex = assertThrows(GraphQLException.class, () -> service.anular(1L));

        assertTrue(ex.getMessage().contains("tesoreria"));
        assertEquals(ValeEstado.CONFIRMADO, v.getEstado());
    }
}
