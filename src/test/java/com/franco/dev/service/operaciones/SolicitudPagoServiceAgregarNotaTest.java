package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoNotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.financiero.CambioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * #435: agregar una nota a una solicitud ya creada recalcula el total, con el monto de la nota convertido a la
 * moneda de la cabecera (el desktop lo mandaba en la moneda de la nota), y solo mientras compras puede editarla.
 */
class SolicitudPagoServiceAgregarNotaTest {

    private SolicitudPagoRepository repository;
    private SolicitudPagoNotaRecepcionRepository relacionRepository;
    private NotaRecepcionRepository notaRepository;
    private SolicitudPagoService service;
    private SolicitudPago solicitud;

    @BeforeEach
    void setUp() {
        repository = mock(SolicitudPagoRepository.class);
        relacionRepository = mock(SolicitudPagoNotaRecepcionRepository.class);
        notaRepository = mock(NotaRecepcionRepository.class);
        SolicitudPagoNotaRecepcionService relacionService =
                new SolicitudPagoNotaRecepcionService(relacionRepository, repository, notaRepository);
        service = new SolicitudPagoService(repository, relacionService, notaRepository,
                mock(ProcesoEtapaService.class), mock(RecepcionMercaderiaNotaService.class),
                mock(RecepcionMercaderiaService.class), mock(MonedaRepository.class),
                mock(FormaPagoRepository.class), mock(CambioService.class));

        solicitud = new SolicitudPago();
        solicitud.setId(7L);
        solicitud.setNumeroSolicitud("SP-000007");
        solicitud.setMoneda(moneda(1L, "GUARANI", 0));
        solicitud.setMontoTotal(100.0);
        when(repository.lockById(7L)).thenReturn(Optional.of(solicitud));
        when(repository.findById(7L)).thenReturn(Optional.of(solicitud));
        when(relacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Moneda moneda(Long id, String denominacion, Integer decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setDecimales(decimales);
        return m;
    }

    private void notaEnDolares() {
        NotaRecepcion n = new NotaRecepcion();
        n.setId(9L);
        n.setMoneda(moneda(3L, "DOLAR", 2));
        n.setCotizacion(7310.5);
        when(notaRepository.findById(9L)).thenReturn(Optional.of(n));
        when(notaRepository.valorTotalConRechazos(9L)).thenReturn(123.45);
    }

    @Test
    void elMontoSaleConvertidoALaMonedaDeLaCabeceraYElTotalSeRecalcula() {
        solicitud.setEstado(SolicitudPagoEstado.PENDIENTE);
        notaEnDolares();
        // Lo que suma la base después de guardar: 100 de antes + la nota convertida.
        when(relacionRepository.getTotalMontoForSolicitudPago(7L)).thenReturn(100.0 + 902481.0);

        SolicitudPagoNotaRecepcion r = service.agregarNota(7L, 9L);

        // 123.45 USD * 7310.5 = 902481.225 Gs, no 123.45
        assertEquals(902481.0, r.getMontoIncluido());
        assertEquals(902581.0, solicitud.getMontoTotal());
    }

    @Test
    void unaDevueltaTambienAceptaNotas() {
        solicitud.setEstado(SolicitudPagoEstado.DEVUELTO);
        notaEnDolares();
        when(relacionRepository.getTotalMontoForSolicitudPago(7L)).thenReturn(902481.0);

        assertNotNull(service.agregarNota(7L, 9L));
    }

    @Test
    void unaSolicitadaRechazaLaNotaSinGuardarNada() {
        solicitud.setEstado(SolicitudPagoEstado.SOLICITADO);
        notaEnDolares();

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.agregarNota(7L, 9L));

        assertEquals("No se puede agregar notas: la solicitud SP-000007 está en SOLICITADO.", e.getMessage());
        verify(relacionRepository, never()).save(any());
        assertEquals(100.0, solicitud.getMontoTotal());
    }
}
