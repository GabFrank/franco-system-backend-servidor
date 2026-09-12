package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class FormatoTerminalPosInput {
    private Long id;
    private String nombre;
    private Long proveedorServicioId;
    /** MAQUINA | WEB | API. Si no viene, el service lo rechaza: no hay default silencioso. */
    private String tipo;
    /** Obligatorio para MAQUINA y WEB; solo API puede no tenerlo. */
    private String patron;
    private String mapeo;
    private String ejemplo;
    private Boolean activo;
    private Long usuarioId;
}
