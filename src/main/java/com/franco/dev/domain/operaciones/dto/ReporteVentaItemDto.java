package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para cada fila del reporte de ventas (JasperReports datasource).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReporteVentaItemDto {
    private Long ventaId;
    private String sucursal;
    private String cliente;
    private String fecha;
    private String formaPago;
    private String moneda;
    private String estado;
    private Double totalGs;
}
