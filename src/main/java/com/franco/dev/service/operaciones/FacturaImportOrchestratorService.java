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

import java.util.Base64;
import java.util.Optional;

/**
 * Entry point del flujo de importacion automatica de facturas de proveedor.
 *
 * Recibe un archivo (imagen JPG/PNG o PDF), lo persiste fisicamente via ImagenMasterService,
 * llama a OpenAI Vision para extraer datos, persiste el resultado en FacturaProveedorImport
 * con estado REVISION_PENDIENTE listo para revision/confirmacion manual del usuario.
 *
 * En Hito 3 se extendera para aceptar rama XML (mimeType application/xml) usando
 * FacturaSifenXmlParserService, retornando el mismo objeto FacturaIaResponse.
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
    private ImagenMasterService imagenMasterService;

    @Autowired
    private FacturaProveedorImportService importService;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * @param archivoBytes contenido binario crudo
     * @param nombreArchivo nombre original (para log/debugging)
     * @param mimeType "image/jpeg", "image/png", "application/pdf" (XML en hito 3)
     * @param usuarioId quien dispara la importacion
     * @return FacturaProveedorImport persistido en estado REVISION_PENDIENTE o ERROR
     */
    public FacturaProveedorImport procesarArchivo(byte[] archivoBytes, String nombreArchivo,
                                                   String mimeType, Long usuarioId) {
        if (archivoBytes == null || archivoBytes.length == 0) {
            throw new IllegalArgumentException("Archivo vacio");
        }
        if (mimeType == null) {
            throw new IllegalArgumentException("mimeType requerido");
        }

        OrigenImport origen = detectarOrigen(mimeType);
        if (origen == OrigenImport.XML_SIFEN) {
            throw new UnsupportedOperationException("Rama XML SIFEN sera habilitada en Hito 3");
        }

        Optional<Usuario> usuarioOpt = usuarioId != null ? usuarioService.findById(usuarioId) : Optional.empty();

        // 1. Crear registro inicial en estado PROCESANDO
        FacturaProveedorImport imp = new FacturaProveedorImport();
        imp.setOrigen(origen);
        imp.setEstado(EstadoImport.PROCESANDO);
        imp.setNombreArchivo(nombreArchivo);
        imp.setUsuario(usuarioOpt.orElse(null));
        imp = importService.save(imp);
        log.info("FacturaProveedorImport id={} creado en estado PROCESANDO origen={}", imp.getId(), origen);

        try {
            // 2. Convertir PDF a JPEG si corresponde
            byte[] jpegBytes;
            String mimeFinal;
            if (origen == OrigenImport.IA_PDF) {
                jpegBytes = pdfConverter.primerPaginaComoJpeg(archivoBytes);
                mimeFinal = "image/jpeg";
            } else {
                jpegBytes = archivoBytes;
                mimeFinal = mimeType;
            }

            // 3. Persistir imagen en ImagenMaster (filesystem + DB)
            String base64 = "data:" + mimeFinal + ";base64," + Base64.getEncoder().encodeToString(jpegBytes);
            ImagenMaster imagenMaster = imagenMasterService.saveImage(
                    base64,
                    TipoReferencia.FACTURA_PROVEEDOR_IMPORTADA,
                    imp.getId(),
                    nombreArchivo != null ? nombreArchivo : ("import-" + imp.getId()),
                    true,
                    usuarioId
            );
            imp.setImagenMaster(imagenMaster);

            // 4. Llamar a OpenAI Vision
            OpenAiVisionService.Resultado resultado = openAi.analizarImagen(jpegBytes, mimeFinal);

            // 5. Persistir json crudo + validado + tokens + modelo
            imp.setJsonCrudo(resultado.rawJson);
            imp.setJsonValidado(MAPPER.writeValueAsString(resultado.data));
            imp.setTokensPrompt(resultado.tokensPrompt);
            imp.setTokensRespuesta(resultado.tokensRespuesta);
            imp.setModeloIa(resultado.modeloUsado);

            // 6. Validar si la IA reporto error logico
            FacturaIaResponse data = resultado.data;
            if (data.getError() != null && !data.getError().isEmpty()) {
                imp.setEstado(EstadoImport.ERROR);
                imp.setErrorMensaje("IA reporto: " + data.getError());
                log.warn("Import id={} - IA reporto error: {}", imp.getId(), data.getError());
            } else {
                imp.setEstado(EstadoImport.REVISION_PENDIENTE);
                log.info("Import id={} - extraccion OK, items={}, tokens={}+{}",
                        imp.getId(),
                        data.getItems() != null ? data.getItems().size() : 0,
                        resultado.tokensPrompt, resultado.tokensRespuesta);
            }

            return importService.save(imp);

        } catch (Exception e) {
            log.error("Fallo procesando import id={}: {}", imp.getId(), e.getMessage(), e);
            imp.setEstado(EstadoImport.ERROR);
            imp.setErrorMensaje(truncar(e.getMessage(), 1000));
            return importService.save(imp);
        }
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
