package com.franco.dev.service.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Resumen RRHH para el self-service mobile del empleado.
 * DTO minimo (regla del sufijo Mobile): saldo de vacaciones, vales pendientes
 * y ultimo recibo. No expone entidades completas.
 */
@Data
public class ResumenRrhhMobileDto {
    private Long funcionarioId;
    private String nombre;
    private Integer saldoVacacionesDias;
    private Long valesPendientesCantidad;
    private BigDecimal valesPendientesMonto;
    private String ultimoReciboPeriodo;
    private BigDecimal ultimoReciboNeto;
}
