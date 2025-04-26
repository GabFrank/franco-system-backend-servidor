package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class CajaSubCategoriaObservacionInput {
    private Long id;
    private String descripcion;
    private Boolean activo;
    private String creadoEn;
    private Long cajaCategoriaObsId;
    private Long usuarioId;
}
