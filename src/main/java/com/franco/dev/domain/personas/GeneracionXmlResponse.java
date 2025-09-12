package com.franco.dev.domain.personas;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GeneracionXmlResponse {
    private boolean procesamientoCorrecto;
    private String codigoRespuesta;
    private String mensajeRespuesta;
    private String timestamp = LocalDateTime.now().toString();
    private String xmlGenerado;
    private String numeroControl;
    private String codigoSeguridad;
    private String qrCode;
    private String fechaHoraFirma;
    private String hash;
    private String archivoXml;
    private String archivoPdf;
    private String estadoDocumento;
    private String mensajeValidacion;
}
