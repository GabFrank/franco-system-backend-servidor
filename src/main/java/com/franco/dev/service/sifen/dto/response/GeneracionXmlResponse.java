package com.franco.dev.service.sifen.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GeneracionXmlResponse extends SifenResponseBase {
    
    private String xmlGenerado;
    private String xmlParaPrevalidador;
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
