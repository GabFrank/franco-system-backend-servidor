package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para cada fila de movimiento de cobro del reporte detallado de ventas (subreport de movimientos).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovimientoReporteDetalladoDto {
    private String operacion;
    private String formaPago;
    private String moneda;
    private Double valorEnMoneda;
    private Double valorEnGs;
}
