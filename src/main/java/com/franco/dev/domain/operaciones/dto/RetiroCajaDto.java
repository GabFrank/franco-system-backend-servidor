package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Un producto de una devolucion lista para retirar: una entrada por cada
 * DevolucionItem, con el identificador de la devolucion a la que pertenece.
 * El nombre viene de cuando se pensaba en una etiqueta por item; la unidad
 * fisica es la devolucion (ver RetiroDevolucionResumenDto).
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
    /** Factor de la presentacion ("x1", "x12"): cuantas unidades base trae cada una. */
    private String presentacion;
}
