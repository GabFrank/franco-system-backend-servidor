package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.util.Date;

@Data
public class SubcategoriaObservacionInput {
    private Long id;
    private String descripcion;
    private Boolean activo;
    private String creadoEn;
    private Long categoriaObservacionId;
    private Long usuarioId;

}
