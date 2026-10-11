package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.franco.dev.service.financiero.EventoInutilizacionDEDocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoInutilizacionDEService;
import com.franco.dev.service.financiero.EventoNominacionDEService;
import com.franco.dev.service.financiero.FacturaLegalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SIFEN solo acepta el evento de cancelacion sobre un documento aprobado. Antes cancelarDE no miraba
 * el estado: cancelarDocumentoElectronico y la cancelacion de una venta mandaban el evento igual y
 * dejaban un evento PENDIENTE o RECHAZADO sobre un documento que nunca valio (#408).
 *
 * El envio a SIFEN es una llamada estatica de la libreria y no se ejercita aca: la consulta de
 * eventos activos, que es el paso siguiente a la validacion, lanza un centinela que corta antes.
 */
class SifenEventoServiceCancelarTest {

    private static final String CDC = "01800000000000000000000000000000000000000000";
    private static final String CENTINELA = "corte del test antes del envio a SIFEN";

    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private EventoCancelacionDEService eventoCancelacionDEService;
    @Mock private EventoInutilizacionDEService eventoInutilizacionDEService;
    @Mock private EventoInutilizacionDEDocumentoElectronicoService eventoInutilizacionDEDocumentoElectronicoService;
    @Mock private EventoNominacionDEService eventoNominacionDEService;
    @Mock private FacturaLegalService facturaLegalService;

    @InjectMocks private SifenEventoService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(eventoCancelacionDEService.findActivosByCdcDocumento(CDC)).thenThrow(new RuntimeException(CENTINELA));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void documentoEn(EstadoDE estado) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(7L);
        de.setSucursalId(1L);
        de.setCdc(CDC);
        de.setEstado(estado);
        when(documentoElectronicoService.findByCdc(CDC)).thenReturn(Optional.of(de));
    }

    @Test
    void sinAprobarNoSeMandaNiSeEscribeNada() {
        for (EstadoDE estado : new EstadoDE[]{EstadoDE.PENDIENTE, EstadoDE.EN_LOTE, EstadoDE.RECHAZADO,
                EstadoDE.CANCELADO, null}) {
            documentoEn(estado);

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> service.cancelarDE(CDC, "motivo"), "estado " + estado);

            assertTrue(e.getMessage().contains(CDC) && e.getMessage().contains(String.valueOf(estado)),
                    "el mensaje dice que documento y en que estado esta: " + e.getMessage());
        }
        // Ni se llego a mirar los eventos anteriores, que es donde empiezan las escrituras.
        verify(eventoCancelacionDEService, never()).findActivosByCdcDocumento(anyString());
        verify(eventoCancelacionDEService, never()).save(any());
    }

    @Test
    void conUnaCancelacionYaAprobadaSeConservaSuMensaje() {
        documentoEn(EstadoDE.CANCELADO);
        when(eventoCancelacionDEService.tieneCancelacionAprobada(7L, 1L)).thenReturn(true);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.cancelarDE(CDC, "motivo"));

        assertTrue(e.getMessage().contains("ya fue cancelado"), e.getMessage());
    }

    @Test
    void aprobadoPasaLaValidacion() {
        documentoEn(EstadoDE.APROBADO);

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.cancelarDE(CDC, "motivo"));

        assertFalse(e instanceof IllegalStateException, "no lo frena la validacion de estado: " + e);
        assertTrue(e.getCause() != null && CENTINELA.equals(e.getCause().getMessage()),
                "llego al paso siguiente, la consulta de eventos activos: " + e);
        verify(eventoCancelacionDEService).findActivosByCdcDocumento(CDC);
        verify(eventoCancelacionDEService, never()).save(any());
    }
}
