package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.roshka.sifen.core.beans.response.RespuestaConsultaLoteDE;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Un código de respuesta que el switch no conocía mandaba el lote a ERROR_PERMANENTE sin tocar sus
 * DE, que quedaban EN_LOTE para siempre: ningún proceso vuelve a mirar un lote en ese estado. Un
 * código nulo reventaba el switch con NPE.
 */
class SifenServiceConsultarLoteTest {

    private LoteDEService loteDEService;
    private DocumentoElectronicoService documentoElectronicoService;
    private SifenService sifenService;

    @BeforeEach
    void setUp() {
        loteDEService = mock(LoteDEService.class);
        documentoElectronicoService = mock(DocumentoElectronicoService.class);
        when(documentoElectronicoService.findByLoteDe(any())).thenReturn(Collections.emptyList());
        sifenService = new SifenService(documentoElectronicoService, loteDEService, null, null, null,
            null, null, null, null, null);
    }

    @Test
    void codigoDesconocidoNoMataElLote() {
        LoteDE lote = loteEnProceso();

        sifenService.aplicarRespuestaConsultaLote(lote, respuesta("0999"));

        assertEquals(EstadoLoteDE.EN_PROCESO, lote.getEstado());
        verify(loteDEService).save(lote);
    }

    @Test
    void codigoNuloNoRevientaNiMataElLote() {
        LoteDE lote = loteEnProceso();

        sifenService.aplicarRespuestaConsultaLote(lote, respuesta(null));

        assertEquals(EstadoLoteDE.EN_PROCESO, lote.getEstado());
    }

    @Test
    void enProcesamientoSigueEnProceso() {
        LoteDE lote = loteEnProceso();

        sifenService.aplicarRespuestaConsultaLote(lote, respuesta("0361"));

        assertEquals(EstadoLoteDE.EN_PROCESO, lote.getEstado());
        verify(loteDEService).save(lote);
    }

    @Test
    void loteInexistenteSiQuedaEnErrorPermanente() {
        LoteDE lote = loteEnProceso();

        sifenService.aplicarRespuestaConsultaLote(lote, respuesta("0360"));

        assertEquals(EstadoLoteDE.ERROR_PERMANENTE, lote.getEstado());
    }

    private static RespuestaConsultaLoteDE respuesta(String codigo) {
        RespuestaConsultaLoteDE respuesta = mock(RespuestaConsultaLoteDE.class);
        when(respuesta.getdCodResLot()).thenReturn(codigo);
        when(respuesta.getdMsgResLot()).thenReturn("mensaje");
        return respuesta;
    }

    private static LoteDE loteEnProceso() {
        LoteDE lote = new LoteDE();
        lote.setId(1L);
        lote.setSucursalId(20L);
        lote.setEstado(EstadoLoteDE.EN_PROCESO);
        lote.setIntentos(1);
        return lote;
    }
}
