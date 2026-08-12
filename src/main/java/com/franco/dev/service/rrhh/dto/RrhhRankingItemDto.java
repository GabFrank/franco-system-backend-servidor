package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Item genérico de ranking del dashboard de RRHH (top por funcionario).
 * principal = valor a rankear (monto); secundario = conteo asociado.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RrhhRankingItemDto {
    private Long funcionarioId;
    private String nombre;
    private BigDecimal principal;
    private Long secundario;
}
