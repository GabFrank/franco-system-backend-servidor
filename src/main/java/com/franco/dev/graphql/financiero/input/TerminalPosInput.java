package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TerminalPosInput {
    private Long id;

    private String descripcion;

    private String codigo;

    private Long cuentaBancariaId;

    private Long monedaId;

    private Boolean activo;

    private LocalDateTime creadoEn;

    private Long usuarioId;
}
