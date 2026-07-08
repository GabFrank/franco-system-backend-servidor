package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

@Data
public class JustificarJornadaInput {
    private Long jornadaId;
    private Long sucursalId;
    private Long funcionarioId;
    private String fecha;
    private String observacion;
    private Long registradoPorId;
}
