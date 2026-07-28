package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para cada fila de observación del reporte detallado de ventas (subreport de observaciones).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaObservacionReporteDetalladoDto {
    private String fecha;
    private String descripcion;
    private String motivo;
}
