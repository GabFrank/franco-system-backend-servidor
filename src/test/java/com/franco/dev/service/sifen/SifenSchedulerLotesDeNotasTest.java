package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.roshka.sifen.core.exceptions.SifenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Las notas de credito y de remision que envia el central quedaban EN_LOTE para siempre: la
 * respuesta de SIFEN solo la traia el scheduler general, apagado en el central porque su tabla de
 * lotes recibe replicados los de facturas de las filiales. Visto en bodega el 2026-09-30 con 11
 * lotes trabados.
 */
class SifenSchedulerLotesDeNotasTest {

    private SifenService sifenService;
    private DocumentoElectronicoService documentoElectronicoService;
    private LoteDEService loteDEService;
    private SifenSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        sifenService = mock(SifenService.class);
        documentoElectronicoService = mock(DocumentoElectronicoService.class);
        loteDEService = mock(LoteDEService.class);
        scheduler = new SifenSchedulerService(sifenService, documentoElectronicoService, loteDEService);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);
        ReflectionTestUtils.setField(scheduler, "notasConsultaEnabled", true);
    }

    @Test
    void consultaSoloLosLotesDeNotas() throws Exception {
        LoteDE lote = lote(1L, LocalDateTime.now().minusMinutes(10));
        encontrar(lote);

        scheduler.consultarLotesDeNotas();

        verify(sifenService).consultarLote(lote);
        verify(loteDEService, never()).findByEstado(any());
    }

    @Test
    void conElSchedulerGeneralPrendidoNoHaceNada() throws Exception {
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
        encontrar(lote(1L, LocalDateTime.now().minusMinutes(10)));

        scheduler.consultarLotesDeNotas();

        verify(loteDEService, never()).findEnProcesoDeNotas();
        verify(sifenService, never()).consultarLote(any());
    }

    @Test
    void conLaBanderaApagadaNoHaceNada() throws Exception {
        ReflectionTestUtils.setField(scheduler, "notasConsultaEnabled", false);
        encontrar(lote(1L, LocalDateTime.now().minusMinutes(10)));

        scheduler.consultarLotesDeNotas();

        verify(loteDEService, never()).findEnProcesoDeNotas();
        verify(sifenService, never()).consultarLote(any());
    }

    @Test
    void salteaElLoteQueYaNoEstaEnProceso() throws Exception {
        LoteDE enLista = lote(1L, LocalDateTime.now().minusMinutes(10));
        LoteDE releido = lote(1L, LocalDateTime.now().minusMinutes(10));
        releido.setEstado(EstadoLoteDE.PROCESADO);
        when(loteDEService.findEnProcesoDeNotas()).thenReturn(Collections.singletonList(enLista));
        when(loteDEService.findByIdAndSucursalId(1L, 20L)).thenReturn(Optional.of(releido));

        scheduler.consultarLotesDeNotas();

        verify(sifenService, never()).consultarLote(any());
    }

    /** El general sumaba intentos y, con max-retries, mandaba a ERROR_RED un lote vivo. */
    @Test
    void unaCaidaDeSifenNoMataElLote() throws Exception {
        LoteDE lote = lote(1L, LocalDateTime.now().minusMinutes(10));
        encontrar(lote);
        doThrow(new SifenException("0000", "sin red")).when(sifenService).consultarLote(lote);

        for (int i = 0; i < 5; i++) {
            scheduler.consultarLotesDeNotas();
        }

        verify(sifenService, times(5)).consultarLote(lote);
        assertEquals(EstadoLoteDE.EN_PROCESO, lote.getEstado());
        assertEquals(1, lote.getIntentos());
        verify(loteDEService, never()).save(any());
    }

    @Test
    void pasadoElCorteConsultaPorCdcYCierraElLote() throws Exception {
        LoteDE lote = lote(1L, LocalDateTime.now().minusHours(50));
        encontrar(lote);
        when(documentoElectronicoService.findByLoteDe(lote))
            .thenReturn(documentos(EstadoDE.EN_LOTE))
            .thenReturn(documentos(EstadoDE.APROBADO));

        scheduler.consultarLotesDeNotas();

        verify(sifenService, never()).consultarLote(any());
        verify(sifenService).consultarDE("CDC-100");
        assertEquals(EstadoLoteDE.PROCESADO, lote.getEstado());
        verify(loteDEService).save(lote);
    }

    @Test
    void siElDeSigueEnLoteElLoteNoCambia() throws Exception {
        LoteDE lote = lote(1L, LocalDateTime.now().minusHours(50));
        encontrar(lote);
        when(documentoElectronicoService.findByLoteDe(lote)).thenReturn(documentos(EstadoDE.EN_LOTE));

        scheduler.consultarLotesDeNotas();

        assertEquals(EstadoLoteDE.EN_PROCESO, lote.getEstado());
        verify(loteDEService, never()).save(any());
    }

    @Test
    void siLaConsultaPorCdcFallaElLoteNoCambia() throws Exception {
        LoteDE lote = lote(1L, LocalDateTime.now().minusHours(50));
        encontrar(lote);
        when(documentoElectronicoService.findByLoteDe(lote)).thenReturn(documentos(EstadoDE.EN_LOTE));
        when(sifenService.consultarDE(anyString())).thenThrow(new RuntimeException("timeout"));

        scheduler.consultarLotesDeNotas();

        assertEquals(EstadoLoteDE.EN_PROCESO, lote.getEstado());
        verify(loteDEService, never()).save(any());
    }

    @Test
    void conElDeRechazadoElLoteQuedaRechazado() throws Exception {
        LoteDE lote = lote(1L, LocalDateTime.now().minusHours(50));
        encontrar(lote);
        when(documentoElectronicoService.findByLoteDe(lote))
            .thenReturn(documentos(EstadoDE.EN_LOTE))
            .thenReturn(documentos(EstadoDE.RECHAZADO));

        scheduler.consultarLotesDeNotas();

        assertEquals(EstadoLoteDE.RECHAZADO, lote.getEstado());
    }

    private void encontrar(LoteDE lote) {
        when(loteDEService.findEnProcesoDeNotas()).thenReturn(Collections.singletonList(lote));
        when(loteDEService.findByIdAndSucursalId(lote.getId(), lote.getSucursalId()))
            .thenReturn(Optional.of(lote));
    }

    private static List<DocumentoElectronico> documentos(EstadoDE estado) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(100L);
        de.setSucursalId(20L);
        de.setCdc("CDC-100");
        de.setEstado(estado);
        return Collections.singletonList(de);
    }

    private static LoteDE lote(Long id, LocalDateTime creadoEn) {
        LoteDE lote = new LoteDE();
        lote.setId(id);
        lote.setSucursalId(20L);
        lote.setEstado(EstadoLoteDE.EN_PROCESO);
        lote.setIntentos(1);
        lote.setProtocolo("1078608212631666445");
        lote.setCreadoEn(creadoEn);
        return lote;
    }
}
