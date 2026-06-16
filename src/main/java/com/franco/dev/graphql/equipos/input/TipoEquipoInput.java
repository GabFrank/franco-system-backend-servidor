package com.franco.dev.graphql.equipos.input;

import lombok.Data;

@Data
public class TipoEquipoInput {
    private Long id;
    private String descripcion;
    private Long sucursalId;
    private Long usuarioId;
}
