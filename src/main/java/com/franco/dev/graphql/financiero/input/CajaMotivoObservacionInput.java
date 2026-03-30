package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class CajaMotivoObservacionInput {
    private Long id;
    private String descripcion;
    private Boolean activo;
    private String creadoEn;
    private Long cajaSubCategoriaObsId;
    private Long usuarioId;
}
