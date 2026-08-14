package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReporteInventarioDto {
    private Long productoId;
    private String descripcion;
    private String codigoBarra;
    private Double cantidadSistema;
    private Double cantidadEncontrada;
    private Double saldo;
    private String estado;
    private String responsable;
    private String fecha;
    private String sucursal;
    private String vencimiento;
    /** Vencido al momento de generar el reporte: alimenta los subtotales del PDF. */
    private Boolean vencido;
}
