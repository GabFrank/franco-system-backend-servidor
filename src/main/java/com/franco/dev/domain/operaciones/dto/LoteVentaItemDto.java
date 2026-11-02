package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * De qué lote salió un ítem de una venta (#429, fase 2, entrega 2). Una fila por ítem y lote:
 * un ítem que se repartió entre dos lotes trae dos filas. Lo que no se atribuyó a un lote viene
 * con loteId nulo y numeroLote "SIN LOTE".
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoteVentaItemDto {
    private Long ventaItemId;
    private Long productoId;
    private Long loteId;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    /** Unidades del ítem que salieron de este lote, en positivo. */
    private Double cantidad;
    /** El movimiento de lote quedó inactivo o la venta está cancelada. */
    private Boolean anulado;
}
