package com.franco.dev.graphql.activos.input;

import lombok.Data;

@Data
public class TipoMuebleInput {
    private Long id;
    private String descripcion;
    private Long familiaMuebleId;
    private Long usuarioId;
}
