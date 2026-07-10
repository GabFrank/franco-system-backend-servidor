package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila del remito de retiro de devoluciones (datasource del reporte Jasper).
 * Una fila por DevolucionItem.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RemitoRetiroFilaDto {
    private String sucursal;
    private String codigo;
    private String descripcion;
    /** Factor de la presentacion, ya formateado ("x1", "x12"). */
    private String presentacion;
    private Double cantidad;
    private String lote;
    private String vencimiento;
}
