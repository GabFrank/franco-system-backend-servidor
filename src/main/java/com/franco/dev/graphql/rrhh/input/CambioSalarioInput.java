package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CambioSalarioInput {
    private Long funcionarioId;
    private BigDecimal nuevoSalario;
    private Long monedaId;
    private String fecha;
    private String motivo;
    private Long autorizadoPorId;
}
