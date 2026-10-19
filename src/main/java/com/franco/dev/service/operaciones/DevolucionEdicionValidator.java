package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.DevolucionEstado;

/**
 * Validador puro (sin dependencias de Spring) de lo que se puede editar de una devolucion
 * segun su estado. Devuelve null si la edicion es valida, o un mensaje de error si no lo es.
 *
 * La baja de stock se hace al SEPARAR a partir de los items. Si despues de eso cambian los
 * items, el origen o el tipo, cancelar o revertir reingresa otra cosa que la que salio. Por
 * eso la cabecera y los items solo se editan en PENDIENTE. En RETIRADO se cargan ademas los
 * datos del canje (cantidad reingresada y su vencimiento), que no tocan lo que ya salio.
 */
public final class DevolucionEdicionValidator {

    private DevolucionEdicionValidator() {
    }

    /** Alta, edicion y borrado de items, y edicion de la cabecera. */
    public static String validarEdicion(DevolucionEstado estado) {
        if (estado == null || estado == DevolucionEstado.PENDIENTE) {
            return null;
        }
        return "La devolucion esta en estado " + estado
                + ": solo se puede modificar mientras esta PENDIENTE";
    }

    /** Los datos del canje de un item se cargan con la devolucion RETIRADA. */
    public static boolean admiteDatosDeCanje(DevolucionEstado estado) {
        return estado == DevolucionEstado.RETIRADO;
    }

    /**
     * Tope del reingreso de un item: no puede ser negativo ni superar lo devuelto.
     * Las dos cantidades van en unidades de la presentacion del item. Un reingreso
     * null equivale a no haber reingresado nada.
     */
    public static String validarReingreso(Double cantidadDevuelta, Double cantidadReingresada, String producto) {
        if (cantidadReingresada == null) {
            return null;
        }
        double devuelta = cantidadDevuelta != null ? cantidadDevuelta : 0.0;
        if (cantidadReingresada < 0) {
            return "La cantidad reingresada de " + producto + " no puede ser negativa";
        }
        if (cantidadReingresada > devuelta) {
            return "La cantidad reingresada de " + producto + " (" + cantidadReingresada
                    + ") no puede superar la devuelta (" + devuelta + ")";
        }
        return null;
    }
}
