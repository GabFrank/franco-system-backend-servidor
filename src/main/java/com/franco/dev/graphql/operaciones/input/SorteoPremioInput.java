package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class SorteoPremioInput {
    private String descripcion;
    private Long productoId;
    private Integer cantidad;
}
