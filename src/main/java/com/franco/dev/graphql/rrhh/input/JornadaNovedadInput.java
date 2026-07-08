package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.JornadaNovedadTipo;
import lombok.Data;

@Data
public class JornadaNovedadInput {
    private Long id;
    private Long funcionarioId;
    private String fecha;
    private JornadaNovedadTipo tipo;
    private Long jornadaId;
    private Long sucursalId;
    private String observacion;
    private Long registradoPorId;
}
