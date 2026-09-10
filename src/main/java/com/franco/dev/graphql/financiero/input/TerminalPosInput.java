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
     * Formato del modelo de aparato.
     * <p>
     * Opcional, y con una consecuencia que hay que tener presente: cuando NO viene, el resolver
     * <b>no toca</b> el formato ya asignado. Es deliberado — el desktop de hoy todavia no manda
     * este campo, y pisarlo con NULL apagaria la venta con tarjeta de esa caja. Para desvincular
     * hay una mutation aparte, {@code desasignarFormatoTerminalPos}.
     */
    private Long formatoTerminalPosId;

    private Boolean activo;

    private LocalDateTime creadoEn;

    private Long usuarioId;
}
