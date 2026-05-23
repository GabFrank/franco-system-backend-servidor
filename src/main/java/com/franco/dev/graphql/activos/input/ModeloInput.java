package com.franco.dev.graphql.activos.input;

import lombok.Data;

@Data
public class ModeloInput {
    private Long id;
    private String descripcion;
    private Long marcaId;
    private Long usuarioId;
}

