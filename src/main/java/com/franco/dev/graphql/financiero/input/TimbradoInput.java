package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class TimbradoInput {
    private Long id;

    private String razonSocial;

    private String ruc;

    private String numero;

    private String fechaInicio;

    private String fechaFin;

    private Boolean activo;

    private Long usuarioId;
}
