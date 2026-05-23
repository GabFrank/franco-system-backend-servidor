package com.franco.dev.graphql.activos.input;

import lombok.Data;

@Data
public class EnteInput {
    private Long id;
    private String tipoEnte;
    private Long referenciaId;
    private Boolean activo;
    private Long usuarioId;
}
