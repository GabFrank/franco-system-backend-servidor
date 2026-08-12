package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class MotivoAveriaInput {
    private Long id;
    private String descripcion;
    private Boolean activo;
    private Boolean generaGasto;
    private Boolean aplicaProveedor;
}
