package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SifenEnvioSincronoServiceTest {

    private SifenService sifenService;
    private SifenEnvioSincronoService envio;

    @BeforeEach
    void setUp() {
        sifenService = mock(SifenService.class);
        envio = new SifenEnvioSincronoService(sifenService);
    }

    @Test
    void enviaElDocumentoEnUnLoteDeUnoYEnEseOrden() throws Exception {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setId(55L);
        de.setSucursalId(1L);   // el lote nace con la sucursal del documento: la columna es NOT NULL
        LoteDE lote = new LoteDE();
        lote.setId(9L);
        lote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
        when(sifenService.crearLote(1L)).thenReturn(lote);

        LoteDE resultado = envio.generarYEnviarSincrono(de);

        assertSame(lote, resultado);
        InOrder orden = inOrder(sifenService);
        orden.verify(sifenService).crearLote(1L);
        orden.verify(sifenService).vincularDocumentosALote(lote, Collections.singletonList(de));
        orden.verify(sifenService).enviarLote(lote);
    }

    @Test
    void sinDocumentoPersistidoNoTocaSifen() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> envio.generarYEnviarSincrono(null));
        assertThrows(IllegalArgumentException.class,
                () -> envio.generarYEnviarSincrono(new DocumentoElectronico()));

        verify(sifenService, never()).crearLote(any());
        verify(sifenService, never()).enviarLote(any());
    }
}
