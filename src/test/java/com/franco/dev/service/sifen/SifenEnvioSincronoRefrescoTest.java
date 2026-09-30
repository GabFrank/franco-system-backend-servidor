package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.LoteDEService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Antes de reenviar o anular una nota EN_LOTE se le pregunta a SIFEN. Con el lote vivo, por lote:
 * mientras SIFEN lo procesa puede contestar «no existe» por el CDC, y consultarDE marcaría
 * RECHAZADO una nota que después sale aprobada.
 */
class SifenEnvioSincronoRefrescoTest {

    private SifenService sifenService;
    private LoteDEService loteDEService;
    private SifenEnvioSincronoService envio;

    @BeforeEach
    void setUp() {
        sifenService = mock(SifenService.class);
        loteDEService = mock(LoteDEService.class);
        envio = new SifenEnvioSincronoService(sifenService, loteDEService);
    }

    @Test
    void conElLoteVivoConsultaPorLoteYNoPorCdc() throws Exception {
        LoteDE lote = lote(EstadoLoteDE.EN_PROCESO, LocalDateTime.now().minusMinutes(5));

        envio.refrescarSiEnLote(de());

        verify(sifenService).consultarLote(lote);
        verify(sifenService, never()).consultarDE(anyString());
    }

    @Test
    void conElLoteMuertoConsultaPorCdc() throws Exception {
        lote(EstadoLoteDE.ERROR_ENVIO, LocalDateTime.now().minusMinutes(5));

        envio.refrescarSiEnLote(de());

        verify(sifenService).consultarDE("CDC-1");
        verify(sifenService, never()).consultarLote(any());
    }

    @Test
    void pasadoElPlazoDelLoteConsultaPorCdc() throws Exception {
        lote(EstadoLoteDE.EN_PROCESO, LocalDateTime.now().minusHours(50));

        envio.refrescarSiEnLote(de());

        verify(sifenService).consultarDE("CDC-1");
        verify(sifenService, never()).consultarLote(any());
    }

    @Test
    void unErrorDeLaConsultaNoSale() throws Exception {
        LoteDE lote = lote(EstadoLoteDE.EN_PROCESO, LocalDateTime.now().minusMinutes(5));
        doThrow(new RuntimeException("timeout")).when(sifenService).consultarLote(lote);

        envio.refrescarSiEnLote(de());
    }

    @Test
    void unDeQueNoEstaEnLoteNoSeConsulta() throws Exception {
        DocumentoElectronico aprobado = de();
        aprobado.setEstado(EstadoDE.APROBADO);

        envio.refrescarSiEnLote(aprobado);

        verifyNoInteractions(sifenService);
    }

    @Test
    void sigueEnProcesoSoloConElLoteEnProceso() {
        lote(EstadoLoteDE.EN_PROCESO, LocalDateTime.now().minusMinutes(5));
        assertTrue(envio.sigueEnProceso(de()));

        lote(EstadoLoteDE.ERROR_ENVIO, LocalDateTime.now().minusMinutes(5));
        assertFalse(envio.sigueEnProceso(de()));
    }

    private LoteDE lote(EstadoLoteDE estado, LocalDateTime creadoEn) {
        LoteDE lote = new LoteDE();
        lote.setId(7L);
        lote.setSucursalId(13L);
        lote.setEstado(estado);
        lote.setProtocolo("1078608212628537005");
        lote.setCreadoEn(creadoEn);
        when(loteDEService.findByIdAndSucursalId(7L, 13L)).thenReturn(Optional.of(lote));
        return lote;
    }

    private static DocumentoElectronico de() {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(1L);
        de.setSucursalId(13L);
        de.setLoteDeId(7L);
        de.setCdc("CDC-1");
        de.setEstado(EstadoDE.EN_LOTE);
        return de;
    }
}
