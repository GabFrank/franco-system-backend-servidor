package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Entradas de stock de un producto que no quedaron atribuidas a un lote, por tipo de movimiento. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntradaSinLoteDto {
    private String tipoMovimiento;
    private Long movimientos;
    /** Unidades que entraron sin lote. */
    private Double cantidad;
}
