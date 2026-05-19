package com.franco.dev.graphql.activos.input;

import lombok.Data;

@Data
public class EnteSucursalInput {
    private Long id;
    private Long enteId;
    private Long sucursalId;
    private Long responsableId;
    private Long usuarioId;
}
