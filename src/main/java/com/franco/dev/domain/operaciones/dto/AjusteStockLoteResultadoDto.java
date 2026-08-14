package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cómo quedó todo después de un ajuste por lote.
 *
 * Se devuelven los saldos ya recalculados y no un simple "ok" para que la pantalla muestre el
 * resultado real y no el que ella misma predijo: entre que el operador abrió el diálogo y confirmó
 * pudo entrar una venta o una transferencia del mismo lote.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AjusteStockLoteResultadoDto {

    /** Movimiento agregado que ancla el ajuste. En una atribución su cantidad es 0. */
    private Long movimientoStockId;

    private Long sucursalId;

    private Long loteId;

    private String numeroLote;

    /** Lo que se escribió en el movimiento agregado: la diferencia, o 0 si fue una atribución. */
    private Double cantidadMovimiento;

    /** Saldo del lote en esa sucursal después del ajuste. */
    private Double saldoLote;

    /** Las tres cuentas del producto después del ajuste. */
    private ResumenStockLoteDto resumen;
}
