package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Central no reintentaba los lotes que quedaban sin enviarse: los DE quedan en EN_LOTE, que no lo
 * busca ni crearYEnviarLotes (PENDIENTE) ni consultarLotesPendientes (lotes EN_PROCESO). Con las
 * notas electronicas, que se envian en el momento, ese hueco es el modo de falla normal.
 */
class SifenSchedulerLotesAtrasadosTest {

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
    }

    @Test
    void reenviaLosLotesQueQuedaronSinEnviarse() throws Exception {
        LoteDE lote = lote(3L, EstadoLoteDE.ERROR_ENVIO, LocalDateTime.now().minusHours(2));
        when(loteDEService.findByEstados(anyList())).thenReturn(Collections.singletonList(lote));
        when(documentoElectronicoService.findByLoteDe(lote)).thenReturn(documentos());
        when(loteDEService.findByIdAndSucursalId(3L, 1L)).thenReturn(Optional.of(lote));

        scheduler.procesarLotesAtrasados();

        verify(sifenService).enviarLote(lote);
    }

    @Test
    void buscaLosTresEstadosRecuperables() {
        when(loteDEService.findByEstados(anyList())).thenReturn(Collections.emptyList());

        scheduler.procesarLotesAtrasados();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<EstadoLoteDE>> captor =
                org.mockito.ArgumentCaptor.forClass((Class) List.class);
        verify(loteDEService).findByEstados(captor.capture());
        assertEquals(3, captor.getValue().size());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().containsAll(java.util.Arrays.asList(
                EstadoLoteDE.PENDIENTE_ENVIO, EstadoLoteDE.ERROR_ENVIO, EstadoLoteDE.ERROR_RED)));
    }

    @Test
    void salteaElLoteReciente() throws Exception {
        LoteDE reciente = lote(4L, EstadoLoteDE.PENDIENTE_ENVIO, LocalDateTime.now().minusSeconds(20));
        when(loteDEService.findByEstados(anyList())).thenReturn(Collections.singletonList(reciente));
        when(documentoElectronicoService.findByLoteDe(reciente)).thenReturn(documentos());

        scheduler.procesarLotesAtrasados();

        verify(sifenService, never()).enviarLote(any());
    }

    @Test
    void elLoteSinDocumentosQuedaEnErrorPermanente() throws Exception {
        LoteDE vacio = lote(5L, EstadoLoteDE.PENDIENTE_ENVIO, LocalDateTime.now().minusHours(1));
        when(loteDEService.findByEstados(anyList())).thenReturn(Collections.singletonList(vacio));
        when(documentoElectronicoService.findByLoteDe(vacio)).thenReturn(Collections.emptyList());

        scheduler.procesarLotesAtrasados();

        assertEquals(EstadoLoteDE.ERROR_PERMANENTE, vacio.getEstado());
        verify(loteDEService).save(vacio);
        verify(sifenService, never()).enviarLote(any());
    }

    @Test
    void siElEnvioFallaElLoteSigueSiendoRecuperable() throws Exception {
        LoteDE lote = lote(6L, EstadoLoteDE.ERROR_RED, LocalDateTime.now().minusHours(3));
        when(loteDEService.findByEstados(anyList())).thenReturn(Collections.singletonList(lote));
        when(documentoElectronicoService.findByLoteDe(lote)).thenReturn(documentos());
        doThrow(new RuntimeException("sin red")).when(sifenService).enviarLote(lote);

        scheduler.procesarLotesAtrasados();

        assertEquals(EstadoLoteDE.ERROR_RED, lote.getEstado(), "el estado recuperable no se pisa");
        assertEquals(1, lote.getIntentos());
        verify(loteDEService).save(lote);
    }

    private static List<DocumentoElectronico> documentos() {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(100L);
        de.setSucursalId(1L);
        return Collections.singletonList(de);
    }

    private static LoteDE lote(Long id, EstadoLoteDE estado, LocalDateTime creadoEn) {
        LoteDE lote = new LoteDE();
        lote.setId(id);
        lote.setSucursalId(1L);
        lote.setEstado(estado);
        lote.setIntentos(0);
        lote.setCreadoEn(creadoEn);
        return lote;
    }
}
