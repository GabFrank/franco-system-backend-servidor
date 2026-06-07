package com.franco.dev.graphql.equipos.input;

import lombok.Data;

@Data
public class ModeloEquipoInput {
    private Long id;
    private String descripcion;
    private Long marcaId;
    private Long usuarioId;
}
