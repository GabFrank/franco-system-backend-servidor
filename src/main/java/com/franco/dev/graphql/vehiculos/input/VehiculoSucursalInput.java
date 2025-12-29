package com.franco.dev.graphql.vehiculos.input;

import lombok.Data;

@Data
public class VehiculoSucursalInput {
    private Long id;
    private Long vehiculoId;
    private Long sucursalId;
    private Long responsableId;
    private Long usuarioId;
}

