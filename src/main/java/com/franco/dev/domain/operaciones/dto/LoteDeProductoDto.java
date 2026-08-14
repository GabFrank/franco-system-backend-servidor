package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Un lote de un producto con el saldo que tiene en una sucursal.
 *
 * Alimenta el buscador de lotes del ajuste de stock. Incluye los lotes con saldo cero en esa
 * sucursal: son los que hacen falta para trazar mercadería que ya estaba pero sin lote asignado.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoteDeProductoDto {

    private Long loteId;

    private String numeroLote;

    private LocalDate fechaVencimiento;

    /** Fecha por la que ordena FEFO. Null si el lote no tiene retiro definido. */
    private LocalDate fechaRetiro;

    private EstadoLote estado;

    /** Saldo en unidades base en la sucursal consultada. Cero si el lote nunca entró ahí. */
    private Double saldo;

    /**
     * Saldo del lote en TODA la red, en unidades base. Es lo que separa "acá no hay" de "no hay en
     * ningún lado": sin este dato los dos casos se ven idénticos —saldo cero— y el operador no
     * puede decidir si tiene que traer mercadería o dar el lote por agotado.
     */
    private Double saldoTotal;
}
