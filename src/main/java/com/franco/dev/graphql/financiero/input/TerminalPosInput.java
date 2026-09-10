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

    private Long proveedorServicioId;

    /**
     * Formato del modelo de aparato. Opcional, como todo campo nuevo de input en este repo:
     * {@code mobile} sigue instalada, consume {@code TerminalPosInput} y solo se actualiza por
     * release manual de Play Store.
     */
    private Long formatoTerminalPosId;

    private Boolean activo;

    private LocalDateTime creadoEn;

    private Long usuarioId;
}
