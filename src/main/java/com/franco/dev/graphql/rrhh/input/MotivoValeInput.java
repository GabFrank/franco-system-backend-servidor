package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

@Data
public class MotivoValeInput {
    private Long id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private Long usuarioId;
}
