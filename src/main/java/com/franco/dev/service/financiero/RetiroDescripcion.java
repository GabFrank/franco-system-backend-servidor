package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Retiro;

/**
 * Descripción del movimiento de caja mayor que nace de un retiro de PDV. Es única a propósito: el
 * poller ({@link RetiroTesoreriaProcesador}) y la verificación ({@link RetiroVerificacionService})
 * tienen que escribir lo mismo para que el historial de caja se lea igual venga por donde venga.
 *
 * <p>{@code Retiro #123 - SUCURSAL X - Caja #456}. La caja es la del PDV de donde salió el retiro
 * (issue #578); si el retiro no la tiene, se omite. Solo aplica a movimientos nuevos: los ya
 * registrados no se reescriben, su caja se ve en el detalle del retiro.
 */
public final class RetiroDescripcion {

    private RetiroDescripcion() { }

    public static String de(Retiro retiro, String nombreSucursal) {
        StringBuilder sb = new StringBuilder("Retiro #").append(retiro.getId()).append(" - ").append(nombreSucursal);
        if (retiro.getCajaSalidaId() != null) sb.append(" - Caja #").append(retiro.getCajaSalidaId());
        return sb.toString();
    }
}
