package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.DevolucionEstado;

/**
 * Validador puro (sin dependencias de Spring) de la maquina de estados de devoluciones.
 * Devuelve null si la transicion es valida, o un mensaje de error si no lo es.
 *
 * Flujo CON_PROVEEDOR:  PENDIENTE -> SEPARADO -> RETIRADO -> (CANJEADO | ACREDITADO)
 * Flujo SIN_PROVEEDOR:  PENDIENTE -> SEPARADO -> DESCARTADO
 * CANCELADA: permitida desde PENDIENTE o SEPARADO.
 */
public final class DevolucionTransicionValidator {

    private DevolucionTransicionValidator() {
    }

    public static boolean esTerminal(DevolucionEstado e) {
        return e == DevolucionEstado.CANJEADO || e == DevolucionEstado.ACREDITADO
                || e == DevolucionEstado.DESCARTADO || e == DevolucionEstado.CANCELADA;
    }

    /**
     * @param actual       estado actual de la devolucion
     * @param nuevo        estado destino
     * @param conProveedor true si la devolucion es CON_PROVEEDOR
     * @return null si la transicion es valida; mensaje de error en caso contrario
     */
    public static String validar(DevolucionEstado actual, DevolucionEstado nuevo, boolean conProveedor) {
        if (actual == null || nuevo == null) {
            return "Estado actual y destino son obligatorios";
        }
        if (esTerminal(actual)) {
            return "La devolucion ya esta finalizada (" + actual + "), no admite cambios de estado";
        }
        if (nuevo == DevolucionEstado.CANCELADA) {
            if (actual != DevolucionEstado.PENDIENTE && actual != DevolucionEstado.SEPARADO) {
                return "Solo se puede cancelar una devolucion PENDIENTE o SEPARADO";
            }
            return null;
        }
        switch (nuevo) {
            case SEPARADO:
                return actual == DevolucionEstado.PENDIENTE ? null : "SEPARADO solo desde PENDIENTE";
            case RETIRADO:
                if (!conProveedor) return "RETIRADO solo aplica a devoluciones con proveedor";
                return actual == DevolucionEstado.SEPARADO ? null : "RETIRADO solo desde SEPARADO";
            case DESCARTADO:
                if (conProveedor) return "DESCARTADO solo aplica a salidas sin proveedor";
                return actual == DevolucionEstado.SEPARADO ? null : "DESCARTADO solo desde SEPARADO";
            case CANJEADO:
            case ACREDITADO:
                if (!conProveedor) return "CANJEADO/ACREDITADO solo aplican a devoluciones con proveedor";
                return actual == DevolucionEstado.RETIRADO ? null : "CANJEADO/ACREDITADO solo desde RETIRADO";
            default:
                return "Transicion no valida hacia " + nuevo;
        }
    }
}
