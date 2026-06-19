package com.franco.dev.service.operaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.operaciones.FacturaProveedorImport;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import com.franco.dev.domain.operaciones.enums.OrigenImport;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.ia.FacturaIaResponse;
import com.franco.dev.service.ia.OpenAiVisionService;
import com.franco.dev.service.ia.PdfConverterService;
import com.franco.dev.service.media.ImagenMasterService;
import com.franco.dev.service.personas.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Entry point del flujo de importacion automatica de facturas de proveedor.
 *
 * Recibe uno o varios archivos (imagenes JPG/PNG, un PDF, o un XML SIFEN), los procesa y
 * persiste el resultado en FacturaProveedorImport con estado REVISION_PENDIENTE listo para
 * revision/confirmacion manual del usuario.
 *
 * Multipagina: una factura legal puede ocupar varias hojas. Tanto un PDF de N paginas como
 * varias fotos se convierten en una lista de imagenes que se envia en una sola llamada a
 * OpenAI Vision, asi el modelo consolida los items de todas las paginas. El XML SIFEN ya
 * contiene la factura completa, por eso acepta un solo archivo.
 */
@Service
public class FacturaImportOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(FacturaImportOrchestratorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private PdfConverterService pdfConverter;

    @Autowired
    private OpenAiVisionService openAi;

    @Autowired
    private FacturaSifenXmlParserService xmlParser;

    @Autowired
    private ImagenMasterService imagenMasterService;

    @Autowired
    private FacturaProveedorImportService importService;

    @Autowired
    private UsuarioService usuarioService;

    /** Un archivo entrante: bytes crudos + nombre + mimeType. */
    public static class ArchivoImport {
        public final byte[] bytes;
        public final String nombreArchivo;
        public final String mimeType;

        public ArchivoImport(byte[] bytes, String nombreArchivo, String mimeType) {
            this.bytes = bytes;
            this.nombreArchivo = nombreArchivo;
            this.mimeType = mimeType;
        }
    }

    /**
     * Overload de un solo archivo. Delega en {@link #procesarArchivos}.
     *
     * @param archivoBytes contenido binario crudo
     * @param nombreArchivo nombre original (para log/debugging)
     * @param mimeType "image/jpeg", "image/png", "application/pdf", "application/xml"
     * @param usuarioId quien dispara la importacion
     * @return FacturaProveedorImport persistido en estado REVISION_PENDIENTE o ERROR
     */
    public FacturaProveedorImport procesarArchivo(byte[] archivoBytes, String nombreArchivo,
                                                   String mimeType, Long usuarioId) {
        return procesarArchivos(
                Collections.singletonList(new ArchivoImport(archivoBytes, nombreArchivo, mimeType)),
                usuarioId);
    }

    /**
     * Procesa una factura compuesta por uno o varios archivos (paginas).
     *
     * @param archivos paginas en orden. Todas deben ser del mismo tipo (todas imagenes, o
     *                 un PDF, o un XML). XML SIFEN acepta un solo archivo.
     * @param usuarioId quien dispara la importacion
     * @return FacturaProveedorImport persistido en estado REVISION_PENDIENTE o ERROR
     */
    public FacturaProveedorImport procesarArchivos(List<ArchivoImport> archivos, Long usuarioId) {
        if (archivos == null || archivos.isEmpty()) {
            throw new IllegalArgumentException("Sin archivos");
        }
        for (ArchivoImport a : archivos) {
            if (a == null || a.bytes == null || a.bytes.length == 0) {
                throw new IllegalArgumentException("Archivo vacio");
            }
            if (a.mimeType == null) {
                throw new IllegalArgumentException("mimeType requerido");
            }
        }

        OrigenImport origen = detectarOrigen(archivos.get(0).mimeType);
        for (ArchivoImport a : archivos) {
            if (detectarOrigen(a.mimeType) != origen) {
                throw new IllegalArgumentException(
                        "No se pueden mezclar tipos de archivo (XML/PDF/imagen) en una misma importacion");
            }
        }
        if (origen == OrigenImport.XML_SIFEN && archivos.size() > 1) {
            throw new IllegalArgumentException(
                    "XML SIFEN: suba un solo archivo (el XML ya contiene la factura completa)");
        }

        String nombreConjunto = archivos.size() == 1
                ? archivos.get(0).nombreArchivo
                : archivos.get(0).nombreArchivo + " (+" + (archivos.size() - 1) + " pag.)";
        Optional<Usuario> usuarioOpt = usuarioId != null ? usuarioService.findById(usuarioId) : Optional.empty();

        // 1. Crear registro inicial en estado PROCESANDO
        FacturaProveedorImport imp = new FacturaProveedorImport();
        imp.setOrigen(origen);
        imp.setEstado(EstadoImport.PROCESANDO);
        imp.setNombreArchivo(nombreConjunto);
        imp.setUsuario(usuarioOpt.orElse(null));
        imp = importService.save(imp);
        log.info("FacturaProveedorImport id={} creado en estado PROCESANDO origen={} archivos={}",
                imp.getId(), origen, archivos.size());

        try {
            if (origen == OrigenImport.XML_SIFEN) {
                return procesarXml(imp, archivos.get(0).bytes);
            }
            return procesarIA(imp, archivos, origen, usuarioId);
        } catch (Exception e) {
            log.error("Fallo procesando import id={}: {}", imp.getId(), e.getMessage(), e);
            imp.setEstado(EstadoImport.ERROR);
            imp.setErrorMensaje(truncar(e.getMessage(), 1000));
            return importService.save(imp);
        }
    }

    private FacturaProveedorImport procesarXml(FacturaProveedorImport imp, byte[] archivoBytes) throws Exception {
        String xmlContent = new String(archivoBytes, java.nio.charset.StandardCharsets.UTF_8);
        FacturaIaResponse data = xmlParser.parsearXml(xmlContent);

        imp.setJsonCrudo("{\"xml\":" + MAPPER.writeValueAsString(xmlContent) + "}");
        imp.setJsonValidado(MAPPER.writeValueAsString(data));
        // No hay tokens IA en XML, no hay modelo
        imp.setModeloIa("XML_SIFEN_PARSER");

        if (data.getError() != null && !data.getError().isEmpty()) {
            imp.setEstado(EstadoImport.ERROR);
            imp.setErrorMensaje("Parser XML reporto: " + data.getError());
            log.warn("Import id={} - XML parser error: {}", imp.getId(), data.getError());
        } else {
            imp.setEstado(EstadoImport.REVISION_PENDIENTE);
            log.info("Import id={} - XML SIFEN parseado, items={}",
                    imp.getId(), data.getItems() != null ? data.getItems().size() : 0);
        }
        return importService.save(imp);
    }

    private FacturaProveedorImport procesarIA(FacturaProveedorImport imp, List<ArchivoImport> archivos,
                                              OrigenImport origen, Long usuarioId) throws Exception {
        // Construir la lista de imagenes (paginas) a enviar a Vision.
        // PDF: cada pagina -> JPEG. Imagen: cada archivo es una pagina.
        List<OpenAiVisionService.ImagenParaAnalisis> imagenes = new ArrayList<>();
        for (ArchivoImport a : archivos) {
            if (origen == OrigenImport.IA_PDF) {
                for (byte[] pageJpeg : pdfConverter.todasLasPaginasComoJpeg(a.bytes)) {
                    imagenes.add(new OpenAiVisionService.ImagenParaAnalisis(pageJpeg, "image/jpeg"));
                }
            } else {
                imagenes.add(new OpenAiVisionService.ImagenParaAnalisis(a.bytes, a.mimeType));
            }
        }

        // Persistir la primera pagina en ImagenMaster como representativa (thumbnail/auditoria).
        OpenAiVisionService.ImagenParaAnalisis primera = imagenes.get(0);
        String base64 = "data:" + primera.mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(primera.bytes);
        ImagenMaster imagenMaster = imagenMasterService.saveImage(
                base64,
                TipoReferencia.FACTURA_PROVEEDOR_IMPORTADA,
                imp.getId(),
                imp.getNombreArchivo() != null ? imp.getNombreArchivo() : ("import-" + imp.getId()),
                true,
                usuarioId
        );
        imp.setImagenMaster(imagenMaster);

        // Llamar a OpenAI Vision con todas las paginas en un solo request
        OpenAiVisionService.Resultado resultado = openAi.analizarImagenes(imagenes);

        // Persistir json crudo + validado + tokens + modelo
        imp.setJsonCrudo(resultado.rawJson);
        imp.setJsonValidado(MAPPER.writeValueAsString(resultado.data));
        imp.setTokensPrompt(resultado.tokensPrompt);
        imp.setTokensRespuesta(resultado.tokensRespuesta);
        imp.setModeloIa(resultado.modeloUsado);

        // Validar si la IA reporto error logico
        FacturaIaResponse data = resultado.data;
        if (data.getError() != null && !data.getError().isEmpty()) {
            imp.setEstado(EstadoImport.ERROR);
            imp.setErrorMensaje("IA reporto: " + data.getError());
            log.warn("Import id={} - IA reporto error: {}", imp.getId(), data.getError());
        } else {
            imp.setEstado(EstadoImport.REVISION_PENDIENTE);
            log.info("Import id={} - extraccion OK, paginas={}, items={}, tokens={}+{}",
                    imp.getId(), imagenes.size(),
                    data.getItems() != null ? data.getItems().size() : 0,
                    resultado.tokensPrompt, resultado.tokensRespuesta);
        }
        return importService.save(imp);
    }

    OrigenImport detectarOrigen(String mimeType) {
        String mt = mimeType.toLowerCase();
        if (mt.contains("xml")) return OrigenImport.XML_SIFEN;
        if (mt.contains("pdf")) return OrigenImport.IA_PDF;
        if (mt.startsWith("image/")) return OrigenImport.IA_IMAGEN;
        throw new IllegalArgumentException("mimeType no soportado: " + mimeType);
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
