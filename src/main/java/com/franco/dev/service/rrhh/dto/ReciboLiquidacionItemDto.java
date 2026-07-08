package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila del recibo de sueldo para el datasource de JasperReports.
 * Los campos deben coincidir con los &lt;field&gt; de recibo-liquidacion.jrxml.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReciboLiquidacionItemDto {
    private String descripcion;
    private String tipo;
    private String monto;
}
