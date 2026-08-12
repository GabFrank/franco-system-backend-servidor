package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Punto de una serie temporal del dashboard de RRHH (agrupado por período
 * 'YYYY-MM'). cantidad = nº de liquidaciones, monto = suma del neto.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RrhhSeriePuntoDto {
    private String periodo;
    private Long cantidad;
    private BigDecimal monto;
}
