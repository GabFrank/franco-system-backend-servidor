package com.franco.dev.service.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * KPIs del dashboard de RRHH para un período (YYYY-MM).
 * Todos los montos en la moneda base; los conteos como Long.
 */
@Data
public class DashboardRrhhKpisDto {
    private String periodo;
    private Long funcionariosActivos;
    private BigDecimal nominaDelMes;
    private Long liquidacionesPendientes;
    private Long valesPendientesCantidad;
    private BigDecimal valesPendientesMonto;
    private Long prestamosActivosCantidad;
    private BigDecimal prestamosActivosSaldo;
    private Long penalizacionesMesCantidad;
    private BigDecimal penalizacionesMesMonto;
    private Long horasExtraMesCantidad;
    private BigDecimal horasExtraMesMonto;
    private Long cuotasVencidasCantidad;
    private BigDecimal aguinaldoEstimadoAnio;
    private Long cumpleanosDelMes;
    private Long vacacionesPorVencer;
}
