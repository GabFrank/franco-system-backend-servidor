package com.franco.dev.graphql.activos.input;

import lombok.Data;

@Data
public class TipoCombustibleInput {
    private Long id;
    private String descripcion;
    private Long usuarioId;
}
