package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para cada fila de producto del reporte detallado de ventas (subreport de items).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaItemReporteDetalladoDto {
    private String producto;
    private String presentacion;
    private Double cantidad;
    private Double precio;
    private Double precioCosto;
    private Double costoTotal;
    private Double total;
}
