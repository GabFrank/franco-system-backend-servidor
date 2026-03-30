package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoriaObservacionInput {

    private Long id;
    private String descripcion;
    private Boolean activo;
    private String creadoEn;
    private Long usuarioId;
}
