package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jasperreports.engine.JRDataSource;

/**
 * DTO para cada venta del reporte detallado de ventas (JasperReports datasource principal).
 * Encabezado de la venta + 3 sub-datasources (items, movimientos, observaciones) consumidos
 * por componentes jr:list del jrxml, uno por venta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReporteVentaDetalladoDto {
    private Long ventaId;
    private String sucursal;
    private String cliente;
    private String fecha;
    private String formaPago;
    private String moneda;
    private String estado;
    private String responsable;
    private Double totalGs;
    private Double totalRecibidoGs;
    private Double totalRecibidoRs;
    private Double totalRecibidoDs;
    private Double totalRecibido;
    private Double totalDescuento;
    private Double totalAumento;
    private Double totalFinal;
    private Double costoTotalVenta;
    private JRDataSource itemsDataSource;
    private JRDataSource movimientosDataSource;
    private JRDataSource observacionesDataSource;
}
