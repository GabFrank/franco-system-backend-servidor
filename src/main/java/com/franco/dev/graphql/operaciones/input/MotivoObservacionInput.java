package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class MotivoObservacionInput {
    private Long id;
    private String descripcion;
    private Boolean activo;
    private String creadoEn;
    private Long subcategoriaObservacionId;
    private Long usuarioId;
}
