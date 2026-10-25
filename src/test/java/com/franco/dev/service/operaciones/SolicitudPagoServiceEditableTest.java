package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoDetalle;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoDetalleRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado.*;
import static com.franco.dev.service.operaciones.SolicitudPagoService.ESTADOS_EDITABLES;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** #435: qué se puede cambiar de una solicitud según su estado, y el borrado de una forma de pago que ya no está. */
class SolicitudPagoServiceEditableTest {

    private SolicitudPagoRepository repository;
    private SolicitudPagoService service;
    private SolicitudPago solicitud;

    @BeforeEach
    void setUp() {
        repository = mock(SolicitudPagoRepository.class);
        service = new SolicitudPagoService(repository, mock(SolicitudPagoNotaRecepcionService.class),
                mock(NotaRecepcionRepository.class), mock(ProcesoEtapaService.class),
                mock(RecepcionMercaderiaNotaService.class), mock(RecepcionMercaderiaService.class),
                mock(MonedaRepository.class), mock(FormaPagoRepository.class), mock(CambioService.class));
        solicitud = new SolicitudPago();
        solicitud.setId(7L);
        solicitud.setNumeroSolicitud("SP-000007");
        when(repository.lockById(7L)).thenReturn(Optional.of(solicitud));
    }

    private boolean acepta(SolicitudPagoEstado estado, java.util.Set<SolicitudPagoEstado> permitidos) {
        solicitud.setEstado(estado);
        try {
            service.exigirEditable(7L, permitidos, "probar");
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Con un pago parcial hecho tampoco se edita: el diálogo del desktop ya lo deja en solo lectura (Franco, 17/10). */
    @Test
    void soloEnBorradorODevuelta() {
        assertTrue(acepta(PENDIENTE, ESTADOS_EDITABLES));
        assertTrue(acepta(DEVUELTO, ESTADOS_EDITABLES));
        for (SolicitudPagoEstado e : new SolicitudPagoEstado[]{SOLICITADO, PARCIAL, CONCLUIDO, CANCELADO}) {
            assertFalse(acepta(e, ESTADOS_EDITABLES), e.name());
        }
    }

    @Test
    void elRechazoDiceQueSolicitudYEnQueEstado() {
        solicitud.setEstado(CONCLUIDO);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.exigirEditable(7L, ESTADOS_EDITABLES, "quitar notas"));
        assertEquals("No se puede quitar notas: la solicitud SP-000007 está en CONCLUIDO.", e.getMessage());
    }

    /** La edición completa pasa por el mismo control con lock: una SOLICITADA no se edita. */
    @Test
    void laEdicionCompletaTambienRespetaElEstado() {
        solicitud.setEstado(SOLICITADO);
        assertThrows(IllegalStateException.class,
                () -> service.actualizarSolicitudPago(7L, 1L, null, null, null, null));
        verify(repository).lockById(7L);
    }

    @Test
    void borrarUnaFormaDePagoQueYaNoEstaDevuelveTrueSinBorrar() {
        SolicitudPagoDetalleRepository detalleRepo = mock(SolicitudPagoDetalleRepository.class);
        SolicitudPagoDetalleService detalleService =
                new SolicitudPagoDetalleService(detalleRepo, mock(MonedaService.class), mock(FormaPagoService.class));
        when(detalleRepo.findById(5L)).thenReturn(Optional.empty());

        assertTrue(detalleService.eliminarDetalle(5L));
        verify(detalleRepo, never()).delete(any());
        verify(detalleRepo, never()).deleteById(any());
    }

    @Test
    void borrarUnaFormaDePagoQueExisteLaBorra() {
        SolicitudPagoDetalleRepository detalleRepo = mock(SolicitudPagoDetalleRepository.class);
        SolicitudPagoDetalleService detalleService =
                new SolicitudPagoDetalleService(detalleRepo, mock(MonedaService.class), mock(FormaPagoService.class));
        SolicitudPagoDetalle d = new SolicitudPagoDetalle();
        when(detalleRepo.findById(5L)).thenReturn(Optional.of(d));

        assertTrue(detalleService.eliminarDetalle(5L));
        verify(detalleRepo).delete(d);
    }
}
