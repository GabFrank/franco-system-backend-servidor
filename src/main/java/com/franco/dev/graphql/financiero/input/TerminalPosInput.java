package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TerminalPosInput {
    private Long id;

    private String descripcion;

    private String codigo;

    /**
     * Donde esta fisicamente el aparato. Opcional, como todo campo nuevo de input en esta entrega.
     * <p>
     * <b>Si no viene, el resolver NO toca la sucursal ya asignada</b>, por la misma razon que el
     * formato: el desktop se actualiza con {@code electron-updater}, que pide consentimiento y se
     * puede posponer indefinidamente. O sea que despues de que central suba va a haber cajas
     * corriendo un desktop que no manda este campo, y cada edicion trivial de una terminal
     * --cambiar la descripcion, activarla-- borraria el dato que alguien acaba de cargar a mano
     * sobre las 24 sucursales.
     * <p>
     * No hace falta un camino explicito para desvincular: una maquina se muda a otra sucursal, no
     * a ninguna.
     */
    private Long sucursalId;

    /**
     * Identificador propio de la maquina. Se normaliza (trim + mayusculas) en el service.
     * <p>
     * Misma regla que {@link #sucursalId}: si no viene, no se toca. Una serie se corrige, no se
     * borra.
     */
    private String serie;

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
