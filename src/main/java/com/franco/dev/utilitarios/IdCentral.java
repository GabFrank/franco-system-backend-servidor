package com.franco.dev.utilitarios;

/**
 * Reparto de ids entre el central y los filiales en las tablas que el filial
 * replica al central: el central genera impares y cada filial genera pares con
 * su secuencia (INCREMENT BY 2). Como las paridades no se cruzan, un id generado
 * de un lado nunca choca con uno generado del otro, y la replicacion no se corta
 * por la clave primaria.
 *
 * Es el mismo esquema que ya usan marcacion, movimiento_stock y
 * movimiento_stock_lote. En la base del central lo refuerza el trigger
 * rechazar_id_de_filial (migracion V223.1), que rechaza cualquier
 * INSERT local con id par en todas esas tablas.
 */
public final class IdCentral {

    private IdCentral() {
    }

    /**
     * Proximo id impar mayor que {@code maximo}.
     */
    public static long siguienteImpar(Long maximo) {
        long siguiente = (maximo == null ? 0L : maximo) + 1L;
        return siguiente % 2 == 0 ? siguiente + 1 : siguiente;
    }

    /**
     * Un id par lo genero un filial.
     */
    public static boolean esDeFilial(Long id) {
        return id != null && id % 2 == 0;
    }
}
