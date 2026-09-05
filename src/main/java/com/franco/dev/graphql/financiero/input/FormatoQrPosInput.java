package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class FormatoQrPosInput {
    private Long id;
    private String nombre;
    private Long proveedorServicioId;
    private String patron;
    private String mapeo;
    private String ejemplo;
    private Boolean activo;
    private Long usuarioId;
}
