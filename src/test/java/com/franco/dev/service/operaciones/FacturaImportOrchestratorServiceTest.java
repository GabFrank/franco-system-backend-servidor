package com.franco.dev.service.operaciones;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.operaciones.FacturaProveedorImport;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import com.franco.dev.domain.operaciones.enums.OrigenImport;
import com.franco.dev.service.ia.FacturaIaResponse;
import com.franco.dev.service.ia.OpenAiVisionService;
import com.franco.dev.service.ia.PdfConverterService;
import com.franco.dev.service.media.ImagenMasterService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacturaImportOrchestratorServiceTest {

    @Mock PdfConverterService pdfConverter;
    @Mock OpenAiVisionService openAi;
    @Mock ImagenMasterService imagenMasterService;
    @Mock FacturaProveedorImportService importService;
    @Mock UsuarioService usuarioService;

    @InjectMocks
    private FacturaImportOrchestratorService orchestrator;

    private FacturaProveedorImport persistedImp;

    @BeforeEach
    void setUp() {
        persistedImp = new FacturaProveedorImport();
        persistedImp.setId(42L);
        lenient().when(importService.save(any())).thenAnswer(inv -> {
            FacturaProveedorImport in = inv.getArgument(0);
            if (in.getId() == null) in.setId(42L);
            return in;
        });
        lenient().when(usuarioService.findById(any())).thenReturn(Optional.empty());
        lenient().when(imagenMasterService.saveImage(anyString(), any(TipoReferencia.class), any(Long.class),
                anyString(), any(Boolean.class), any())).thenReturn(mockImagen());
    }

    @Test
    void detectarOrigen_xml() {
        assertEquals(OrigenImport.XML_SIFEN, orchestrator.detectarOrigen("application/xml"));
        assertEquals(OrigenImport.XML_SIFEN, orchestrator.detectarOrigen("text/xml"));
    }

    @Test
    void detectarOrigen_pdf() {
        assertEquals(OrigenImport.IA_PDF, orchestrator.detectarOrigen("application/pdf"));
    }

    @Test
    void detectarOrigen_imagen() {
        assertEquals(OrigenImport.IA_IMAGEN, orchestrator.detectarOrigen("image/jpeg"));
        assertEquals(OrigenImport.IA_IMAGEN, orchestrator.detectarOrigen("image/png"));
    }

    @Test
    void detectarOrigen_otro_lanza() {
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.detectarOrigen("application/json"));
    }

    @Test
    void procesarArchivo_xml_lanzaUnsupportedEnHito2() {
        assertThrows(UnsupportedOperationException.class,
                () -> orchestrator.procesarArchivo(new byte[]{1, 2}, "f.xml", "application/xml", 1L));
    }

    @Test
    void procesarArchivo_imagen_flujoFeliz_estadoRevisionPendiente() throws Exception {
        OpenAiVisionService.Resultado mockResultado = mockResultadoIa("80012345-6", false);
        when(openAi.analizarImagen(any(), eq("image/jpeg"))).thenReturn(mockResultado);

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{1, 2, 3}, "factura.jpg", "image/jpeg", 1L);

        assertEquals(EstadoImport.REVISION_PENDIENTE, result.getEstado());
        assertEquals(OrigenImport.IA_IMAGEN, result.getOrigen());
        assertEquals("gpt-4o", result.getModeloIa());
        assertEquals(100, result.getTokensPrompt());
        assertEquals(50, result.getTokensRespuesta());
        assertNotNull(result.getJsonValidado());
        assertNotNull(result.getJsonCrudo());
        assertNull(result.getErrorMensaje());

        // Verifica que se llamo a saveImage con tipo correcto
        ArgumentCaptor<TipoReferencia> tipoCaptor = ArgumentCaptor.forClass(TipoReferencia.class);
        verify(imagenMasterService).saveImage(anyString(), tipoCaptor.capture(), eq(42L),
                anyString(), eq(true), eq(1L));
        assertEquals(TipoReferencia.FACTURA_PROVEEDOR_IMPORTADA, tipoCaptor.getValue());

        // PdfConverter NO debe ser invocado para imagen
        verify(pdfConverter, never()).primerPaginaComoJpeg(any());
    }

    @Test
    void procesarArchivo_pdf_convierteAJpegAntesDeIA() throws Exception {
        byte[] jpegConvertido = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        when(pdfConverter.primerPaginaComoJpeg(any())).thenReturn(jpegConvertido);
        when(openAi.analizarImagen(eq(jpegConvertido), eq("image/jpeg")))
                .thenReturn(mockResultadoIa("99999-1", true));

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{0x25, 0x50, 0x44, 0x46}, "factura.pdf", "application/pdf", 1L);

        assertEquals(EstadoImport.REVISION_PENDIENTE, result.getEstado());
        assertEquals(OrigenImport.IA_PDF, result.getOrigen());
        verify(pdfConverter).primerPaginaComoJpeg(any());
        verify(openAi).analizarImagen(eq(jpegConvertido), eq("image/jpeg"));
    }

    @Test
    void procesarArchivo_iaDevuelveError_marcaEstadoError() throws Exception {
        FacturaIaResponse errorResp = new FacturaIaResponse();
        errorResp.setError("NO_ES_DOCUMENTO_VALIDO");
        OpenAiVisionService.Resultado res = new OpenAiVisionService.Resultado(
                errorResp, "{\"error\":\"NO_ES_DOCUMENTO_VALIDO\"}", 30, 10, "gpt-4o");
        when(openAi.analizarImagen(any(), anyString())).thenReturn(res);

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{1}, "no-factura.jpg", "image/jpeg", 1L);

        assertEquals(EstadoImport.ERROR, result.getEstado());
        assertTrue(result.getErrorMensaje().contains("NO_ES_DOCUMENTO_VALIDO"));
    }

    @Test
    void procesarArchivo_iaLanzaIOException_marcaError_noPropaga() throws Exception {
        when(openAi.analizarImagen(any(), anyString())).thenThrow(new IOException("HTTP 500"));

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{1}, "x.jpg", "image/jpeg", 1L);

        assertEquals(EstadoImport.ERROR, result.getEstado());
        assertTrue(result.getErrorMensaje().contains("HTTP 500"));
    }

    @Test
    void procesarArchivo_pdfConverterFalla_marcaError() throws Exception {
        when(pdfConverter.primerPaginaComoJpeg(any())).thenThrow(new IOException("PDF corrupto"));

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{0x25, 0x50}, "bad.pdf", "application/pdf", 1L);

        assertEquals(EstadoImport.ERROR, result.getEstado());
        assertTrue(result.getErrorMensaje().contains("PDF corrupto"));
        verify(openAi, never()).analizarImagen(any(), anyString());
    }

    @Test
    void procesarArchivo_archivoVacio_lanza() {
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.procesarArchivo(new byte[0], "x", "image/jpeg", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.procesarArchivo(null, "x", "image/jpeg", 1L));
    }

    @Test
    void procesarArchivo_mimeNull_lanza() {
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.procesarArchivo(new byte[]{1}, "x", null, 1L));
    }

    // -- helpers --

    private static OpenAiVisionService.Resultado mockResultadoIa(String ruc, boolean esLegal) {
        FacturaIaResponse r = new FacturaIaResponse();
        r.setEmisorRuc(ruc);
        r.setEmisorNombre("MOCK EMISOR");
        r.setEsLegal(esLegal);
        return new OpenAiVisionService.Resultado(r, "{\"raw\":\"mock\"}", 100, 50, "gpt-4o");
    }

    private static ImagenMaster mockImagen() {
        ImagenMaster i = new ImagenMaster();
        i.setId(99L);
        i.setUrl("/fake/path/image-99.jpg");
        return i;
    }
}
