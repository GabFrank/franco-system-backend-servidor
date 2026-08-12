package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumen agregado del modulo de devoluciones para el dashboard.
 * Los conteos salen de la entidad Devolucion; los valores (costoUnitario x
 * cantidad) se completan aparte porque agregan a nivel DevolucionItem.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumenDevolucionesDto {
    private Long total;
    private Long conProveedor;
    private Long sinProveedor;
    /** CON_PROVEEDOR en PENDIENTE o SEPARADO: lo que falta retirar. */
    private Long pendientesRetiro;
    private Double valorTotal;
    /** Valor de las devoluciones SIN_PROVEEDOR (merma). */
    private Double valorMerma;

    /** Constructor de conteos (JPQL). Los valores se setean luego. */
    public ResumenDevolucionesDto(Long total, Long conProveedor, Long sinProveedor, Long pendientesRetiro) {
        this.total = total;
        this.conProveedor = conProveedor;
        this.sinProveedor = sinProveedor;
        this.pendientesRetiro = pendientesRetiro;
        this.valorTotal = 0.0;
        this.valorMerma = 0.0;
    }
}
