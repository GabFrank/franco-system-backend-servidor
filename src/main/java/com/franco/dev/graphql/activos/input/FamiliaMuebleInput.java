package com.franco.dev.graphql.activos.input;

import lombok.Data;

@Data
public class FamiliaMuebleInput {
    private Long id;
    private String descripcion;
    private Long usuarioId;
}
