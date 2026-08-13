package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Las tres cuentas de un producto en una sucursal, juntas y en unidades base.
 *
 * Existe para que la pantalla de ajuste pueda mostrar el efecto de lo que está por hacer antes de
 * confirmarlo: cuánto hay en total, cuánto está atribuido a lotes reales y cuánto quedó sin trazar.
 * Devolverlas juntas y ya calculadas evita que el frontend reste dos consultas y se equivoque.
 *
 * {@code sinTrazar} se deriva, no se almacena: es {@code existencia - enLotes}. Puede dar negativo,
 * y eso no es un error: significa que se vendió más de lo que el ledger tenía atribuido, o sea que
 * hay deuda de trazabilidad.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumenStockLoteDto {

    private Long productoId;

    private Long sucursalId;

    /** Existencia agregada, la fuente de verdad del stock total. */
    private Double existencia;

    /** Suma del ledger atribuida a lotes REALES. Las filas SIN LOTE no cuentan acá. */
    private Double enLotes;

    /** existencia - enLotes. Lo que hay pero no se sabe de qué lote es. */
    private Double sinTrazar;
}
