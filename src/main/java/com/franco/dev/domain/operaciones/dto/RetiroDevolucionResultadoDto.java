package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de intentar retirar una devolucion en el retiro en bloque.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetiroDevolucionResultadoDto {
    private Long id;
    private Boolean ok;
    private String mensaje;
}
