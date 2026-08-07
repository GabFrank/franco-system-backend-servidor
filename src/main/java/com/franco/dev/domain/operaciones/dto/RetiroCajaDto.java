package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Desglose por caja fisica: una entrada por cada DevolucionItem, con el
 * identificador de la devolucion a la que pertenece.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetiroCajaDto {
    /** Identificador imprimible de la devolucion (DEV-{sucursalId}-{id}). */
    private String identificador;
    private Long devolucionId;
    private Long productoId;
    private String descripcion;
    private Double cantidad;
    private String lote;
    private LocalDate vencimiento;
}
