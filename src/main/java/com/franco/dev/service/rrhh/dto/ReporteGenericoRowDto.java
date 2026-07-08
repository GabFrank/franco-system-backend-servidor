package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila generica de 4 columnas para reporte-rrhh-generico.jrxml. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReporteGenericoRowDto {
    private String c1;
    private String c2;
    private String c3;
    private String c4;
}
