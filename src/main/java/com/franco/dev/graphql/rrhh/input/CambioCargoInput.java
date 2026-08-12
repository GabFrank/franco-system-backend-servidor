package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

@Data
public class CambioCargoInput {
    private Long funcionarioId;
    private Long cargoId;
    private String fecha;
    private String motivo;
    private Long autorizadoPorId;
}
