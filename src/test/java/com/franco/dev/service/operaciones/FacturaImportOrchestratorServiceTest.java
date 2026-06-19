package com.franco.dev.service.operaciones;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.operaciones.FacturaProveedorImport;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import com.franco.dev.domain.operaciones.enums.OrigenImport;
import com.franco.dev.service.ia.FacturaIaResponse;
import com.franco.dev.service.ia.ImagenCalidadValidator;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacturaImportOrchestratorServiceTest {

    @Mock PdfConverterService pdfConverter;
    @Mock ImagenCalidadValidator calidadValidator;
    @Mock OpenAiVisionService openAi;
    @Mock FacturaSifenXmlParserService xmlParser;
    @Mock ImagenMasterService imagenMasterService;
    @Mock FacturaProveedorImportService importService;
    @Mock UsuarioService usuarioService;

    @InjectMocks
    private FacturaImportOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
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
    void procesarArchivo_xml_llamaParserNoIA_estadoRevisionPendiente() throws Exception {
        FacturaIaResponse mockResp = new FacturaIaResponse();
        mockResp.setEmisorRuc("80012345-6");
        mockResp.setEsLegal(Boolean.TRUE);
        when(xmlParser.parsearXml(anyString())).thenReturn(mockResp);

        String xml = "<rDE><DE>...</DE></rDE>";
        FacturaProveedorImport result = orchestrator.procesarArchivo(
                xml.getBytes(), "factura.xml", "application/xml", 1L);

        assertEquals(EstadoImport.REVISION_PENDIENTE, result.getEstado());
        assertEquals(OrigenImport.XML_SIFEN, result.getOrigen());
        assertEquals("XML_SIFEN_PARSER", result.getModeloIa());
        assertNotNull(result.getJsonValidado());
        verify(xmlParser).parsearXml(xml);
        // XML no usa imagen ni IA
        verify(openAi, never()).analizarImagen(any(), anyString());
        verifyNoInteractions(imagenMasterService);
        verifyNoInteractions(pdfConverter);
        verifyNoInteractions(calidadValidator);
    }

    @Test
    void procesarArchivo_xml_parserReportaError_marcaEstadoError() {
        FacturaIaResponse errorResp = new FacturaIaResponse();
        errorResp.setError("NO_ES_DE_SIFEN");
        when(xmlParser.parsearXml(anyString())).thenReturn(errorResp);

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                "<otro/>".getBytes(), "x.xml", "text/xml", 1L);

        assertEquals(EstadoImport.ERROR, result.getEstado());
        assertTrue(result.getErrorMensaje().contains("NO_ES_DE_SIFEN"));
    }

    @Test
    void procesarArchivo_imagen_flujoFeliz_estadoRevisionPendiente() throws Exception {
        when(openAi.analizarImagen(any(), anyString())).thenReturn(mockResultadoIa("80012345-6", false));

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{1, 2, 3}, "factura.jpg", "image/jpeg", 1L);

        assertEquals(EstadoImport.REVISION_PENDIENTE, result.getEstado());
        assertEquals(OrigenImport.IA_IMAGEN, result.getOrigen());
        assertEquals("gpt-4o", result.getModeloIa());
        assertNotNull(result.getJsonValidado());
        assertNotNull(result.getJsonCrudo());
        assertNull(result.getErrorMensaje());

        // la imagen subida se valida; el PDF converter NO se toca
        verify(calidadValidator).validar(any(), eq("factura.jpg"));
        verify(pdfConverter, never()).todasLasPaginasComoJpeg(any());

        ArgumentCaptor<TipoReferencia> tipoCaptor = ArgumentCaptor.forClass(TipoReferencia.class);
        verify(imagenMasterService).saveImage(anyString(), tipoCaptor.capture(), eq(42L),
                anyString(), eq(true), eq(1L));
        assertEquals(TipoReferencia.FACTURA_PROVEEDOR_IMPORTADA, tipoCaptor.getValue());
    }

    @Test
    void procesarArchivo_imagenBajaResolucion_marcaError_noLlamaIA() throws Exception {
        doThrow(new IllegalArgumentException("Imagen de baja resolucion (1080x1480)"))
                .when(calidadValidator).validar(any(), anyString());

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{1, 2, 3}, "wa.jpg", "image/jpeg", 1L);

        assertEquals(EstadoImport.ERROR, result.getEstado());
        assertTrue(result.getErrorMensaje().contains("baja resolucion"));
        verify(openAi, never()).analizarImagen(any(), anyString());
    }

    @Test
    void procesarArchivo_pdf_analizaCadaPaginaPorSeparado() throws Exception {
        byte[] page1 = new byte[]{(byte) 0xFF, (byte) 0xD8, 1};
        byte[] page2 = new byte[]{(byte) 0xFF, (byte) 0xD8, 2};
        when(pdfConverter.todasLasPaginasComoJpeg(any())).thenReturn(Arrays.asList(page1, page2));
        when(openAi.analizarImagen(any(), anyString())).thenReturn(mockResultadoIa("99999-1", true));

        FacturaProveedorImport result = orchestrator.procesarArchivo(
                new byte[]{0x25, 0x50, 0x44, 0x46}, "factura.pdf", "application/pdf", 1L);

        assertEquals(EstadoImport.REVISION_PENDIENTE, result.getEstado());
        assertEquals(OrigenImport.IA_PDF, result.getOrigen());
        verify(pdfConverter).todasLasPaginasComoJpeg(any());
        // UNA llamada Vision por pagina (2 paginas -> 2 llamadas), no una sola con ambas
        verify(openAi, times(2)).analizarImagen(any(), eq("image/jpeg"));
        // las paginas de PDF no pasan por el validador de imagen subida
        verifyNoInteractions(calidadValidator);
    }

    @Test
    void procesarArchivos_variasImagenes_unaLlamadaPorPagina() throws Exception {
        when(openAi.analizarImagen(any(), anyString())).thenReturn(mockResultadoIa("80012345-6", true));

        List<FacturaImportOrchestratorService.ArchivoImport> archivos = Arrays.asList(
                new FacturaImportOrchestratorService.ArchivoImport(new byte[]{1}, "p1.jpg", "image/jpeg"),
                new FacturaImportOrchestratorService.ArchivoImport(new byte[]{2}, "p2.jpg", "image/jpeg"),
                new FacturaImportOrchestratorService.ArchivoImport(new byte[]{3}, "p3.jpg", "image/jpeg"));

        FacturaProveedorImport result = orchestrator.procesarArchivos(archivos, 1L);

        assertEquals(EstadoImport.REVISION_PENDIENTE, result.getEstado());
        verify(openAi, times(3)).analizarImagen(any(), anyString());
        verify(calidadValidator, times(3)).validar(any(), anyString());
        assertTrue(result.getNombreArchivo().contains("+2"));
    }

    @Test
    void procesarArchivos_mezclaTipos_lanza() {
        List<FacturaImportOrchestratorService.ArchivoImport> archivos = Arrays.asList(
                new FacturaImportOrchestratorService.ArchivoImport(new byte[]{1}, "p1.jpg", "image/jpeg"),
                new FacturaImportOrchestratorService.ArchivoImport(new byte[]{2}, "doc.pdf", "application/pdf"));

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.procesarArchivos(archivos, 1L));
    }

    @Test
    void procesarArchivos_xmlMultiple_lanza() {
        List<FacturaImportOrchestratorService.ArchivoImport> archivos = Arrays.asList(
                new FacturaImportOrchestratorService.ArchivoImport("<a/>".getBytes(), "a.xml", "application/xml"),
                new FacturaImportOrchestratorService.ArchivoImport("<b/>".getBytes(), "b.xml", "application/xml"));

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.procesarArchivos(archivos, 1L));
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
        when(pdfConverter.todasLasPaginasComoJpeg(any())).thenThrow(new IOException("PDF corrupto"));

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

    @Test
    void procesarArchivos_listaVacia_lanza() {
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.procesarArchivos(Collections.emptyList(), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.procesarArchivos(null, 1L));
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
