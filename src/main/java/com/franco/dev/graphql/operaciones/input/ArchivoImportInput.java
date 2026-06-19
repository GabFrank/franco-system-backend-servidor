package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/**
 * Un archivo (pagina) de una importacion de factura. Una factura puede subirse como un solo
 * archivo (XML, PDF o una imagen) o como varias imagenes/paginas.
 */
@Data
public class ArchivoImportInput {
    /** Contenido del archivo en base64 (acepta prefijo data URI). */
    private String archivoBase64;
    /** Nombre original del archivo (para log/auditoria). */
    private String nombreArchivo;
    /** "application/xml", "application/pdf", "image/jpeg", "image/png", etc. */
    private String mimeType;
}
