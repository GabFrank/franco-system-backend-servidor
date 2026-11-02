package com.franco.dev.service.operaciones.compras;

import java.text.Normalizer;
import java.util.Locale;

/**
 * La descripción del renglón tal como la escribe el proveedor, llevada a una clave estable: es lo que se aprende
 * cuando la factura (sobre todo la de papel) no trae el código del proveedor. Solo se compara igual a igual.
 */
public final class DescripcionProveedor {

    static final int LARGO_MAXIMO = 500;

    private DescripcionProveedor() {
    }

    /** Mayúsculas, sin tildes, solo letras y números con un espacio entre palabras; vacío → {@code null}. */
    public static String normalizar(String descripcion) {
        if (descripcion == null) return null;
        String t = Normalizer.normalize(descripcion.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
        if (t.length() > LARGO_MAXIMO) t = t.substring(0, LARGO_MAXIMO).trim();
        return t.isEmpty() ? null : t;
    }
}
