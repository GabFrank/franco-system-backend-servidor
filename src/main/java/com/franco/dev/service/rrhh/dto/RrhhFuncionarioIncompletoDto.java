package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Funcionario con datos de legajo incompletos. score = 1..10 (1 = poco/nada
 * configurado, 10 = completo). faltantes = etiquetas de lo que falta cargar.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RrhhFuncionarioIncompletoDto {
    private Long funcionarioId;
    private String nombre;
    private String cargo;
    private Integer score;
    private String faltantes;
}
