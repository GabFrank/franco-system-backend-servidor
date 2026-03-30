package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class CajaObservacionInput {
    private Long id;
    private String descripcion;
    private String creadoEn;
    private Long cajaMotivoObservacionId;
    private Long cajaId;
    private Long sucursalId;
    private Long usuarioId;
}
