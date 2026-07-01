package com.franco.dev.graphql.equipos.input;

import lombok.Data;

@Data
public class MarcaEquipoInput {
    private Long id;
    private String descripcion;
    private Long usuarioId;
}
