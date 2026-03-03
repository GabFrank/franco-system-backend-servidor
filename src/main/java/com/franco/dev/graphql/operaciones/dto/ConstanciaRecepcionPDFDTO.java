package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para respuesta de PDF de constancia de recepción
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstanciaRecepcionPDFDTO {
    
    private String pdfBase64;
    private String nombreArchivo;
    private Long tamanioBytes;
    private LocalDateTime fechaGeneracion;
} 