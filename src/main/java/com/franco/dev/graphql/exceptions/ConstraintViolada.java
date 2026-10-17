package com.franco.dev.graphql.exceptions;

import java.util.regex.Pattern;

/**
 * Reconoce la violación de un constraint de la base en una excepción del save. El nombre del
 * constraint suele quedar en la causa raíz (PSQLException), no en el mensaje del wrapper de Spring.
 */
public final class ConstraintViolada {

    private ConstraintViolada() {
    }

    public static boolean en(Throwable ex, String constraint) {
        // Palabra entera: "categoria_observacion_unique" está dentro de "caja_categoria_observacion_unique".
        Pattern nombre = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(constraint) + "(?![A-Za-z0-9_])");
        // El tope corta un ciclo de causas.
        int profundidad = 0;
        for (Throwable t = ex; t != null && profundidad < 20; t = t.getCause(), profundidad++) {
            if (t.getMessage() != null && nombre.matcher(t.getMessage()).find()) return true;
        }
        return false;
    }
}
