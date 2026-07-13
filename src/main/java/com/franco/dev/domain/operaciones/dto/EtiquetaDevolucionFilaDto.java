package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila de etiqueta de separado (una por DevolucionItem). Datasource del reporte
 * Jasper y base del ticket termico. El QR lleva el identificador de la caja.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EtiquetaDevolucionFilaDto {
    private String identificador;
    private String codigo;
    private String producto;
    /** Ej. "3 UNIDAD (x1) = 3 UN". */
    private String cantidad;
    private String motivo;
    /** Contenido del QR = identificador de la caja/devolucion. */
    private String qr;
}
