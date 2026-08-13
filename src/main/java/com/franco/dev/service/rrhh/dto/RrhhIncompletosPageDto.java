package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Página de funcionarios con legajo incompleto (peores primero). total = cantidad
 * total de incompletos; items = la porción de la página pedida.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RrhhIncompletosPageDto {
    private Integer total;
    private List<RrhhFuncionarioIncompletoDto> items;
}
