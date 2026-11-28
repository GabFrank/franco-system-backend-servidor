package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** Quién, cuándo y por qué se anuló una operación financiera (sale del contra-movimiento de la anulación). */
@Data
@AllArgsConstructor
public class OperacionFinancieraAnulacion {
    private String motivo;
    private Usuario usuario;
    private LocalDateTime fecha;
}
