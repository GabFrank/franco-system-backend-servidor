package com.franco.dev.repository.financiero;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Un retiro del PDV que ya salió de los flotantes: dónde entró, quién y cuándo. Es lo que el diálogo
 * de ingreso a caja mayor le muestra a quien busca una caja que ya no tiene nada pendiente.
 *
 * <p>{@code movimientoId} es null cuando el marcador del retiro es {@code -1}: se procesó sin montos
 * (verificación o poller con todo en cero) o se dio por procesado en una limpieza, sin movimiento.
 * En ese caso {@code usuario} e {@code ingresadoEn} también son null, y {@code cajaVirtualId} lo es
 * si nunca se le asignó caja mayor. {@code usuario} null con movimiento = lo ingresó el poller.</p>
 */
@Getter
@AllArgsConstructor
public class RetiroIngresadoDto {

    private final Long retiroId;
    private final Long sucursalId;
    private final String sucursalNombre;
    private final Long cajaSalidaId;
    private final Long cajaVirtualId;
    private final String cajaVirtualNombre;
    private final Long movimientoId;
    private final String usuario;
    private final LocalDateTime ingresadoEn;
}
