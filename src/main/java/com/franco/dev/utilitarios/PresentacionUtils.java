package com.franco.dev.utilitarios;

import com.franco.dev.domain.productos.Presentacion;

/**
 * Helpers de presentacion de producto compartidos entre servicios.
 */
public class PresentacionUtils {

    private PresentacionUtils() {
    }

    /**
     * Formatea la presentacion por su factor ("x1", "x12"). Al retirar o separar
     * mercaderia importa cuantas unidades base entran en cada bulto, no como se
     * llama la presentacion.
     */
    public static String formatearFactor(Presentacion pres) {
        if (pres == null || pres.getCantidad() == null) return "";
        double factor = pres.getCantidad();
        if (factor == Math.rint(factor)) return "x" + (long) factor;
        return "x" + factor;
    }
}
