package com.franco.dev.graphql.vehiculos.input;

import lombok.Data;

@Data
public class TipoVehiculoInput {
    private Long id;
    private String descripcion;
    private Long usuarioId;
}

