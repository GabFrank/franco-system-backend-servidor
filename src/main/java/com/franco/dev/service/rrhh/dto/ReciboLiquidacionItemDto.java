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
    /** Categoria del movimiento (SUELDO, ANTICIPOS, IPS, BONUS, ...). */
    private String operacion;
    /** ENTRADA (haber) o SALIDA (descuento). */
    private String tipoMovimiento;
    /** Detalle libre. */
    private String observacion;
    /** Fecha del movimiento origen (o del periodo si no aplica). */
    private String fecha;
    private String monto;
}
