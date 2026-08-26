package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Un lote con saldo que ningún renglón de la toma contó.
 *
 * La finalización deja esos productos ENTEROS fuera del ajuste, con la misma regla que ya aplica al
 * ítem con {@code cantidad == null}: un lote sin contar no es un lote en cero.
 *
 * Este DTO existe para poder avisarlo ANTES de finalizar. Trae la descripción del producto ya
 * resuelta porque el frontend es capa de presentación y no tiene que salir a buscarla.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoteSinContarDto {

    private Long loteId;
    private String numeroLote;
    private Long productoId;
    private String productoDescripcion;
    private LocalDate fechaVencimiento;
    private LocalDate fechaRetiro;
    /** Saldo en unidades base en la sucursal de la toma. */
    private Double saldo;
}
