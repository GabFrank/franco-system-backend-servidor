package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cumpleaños de un funcionario activo dentro del mes del período. dia = día del
 * mes (1-31); cargo opcional para el detalle.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RrhhCumpleanosDto {
    private Long funcionarioId;
    private String nombre;
    private Integer dia;
    private String cargo;
}
