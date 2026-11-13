package com.franco.dev.utilitarios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pageable para resolvers que reciben page/size como Integer opcionales (#582).
 *
 * Con PageRequest.of(page, size) directo, un page o size nulo tira NullPointerException y uno
 * negativo o cero tira IllegalArgumentException. Aca se reemplazan por la primera pagina de
 * {@link #SIZE_POR_DEFECTO}, el mismo valor que ya usaban ventasPorCajaId y ventasGenericFilter.
 *
 * Un size mayor a {@link #SIZE_MAXIMO} se recorta al maximo (punto 3 de #582): una sola consulta
 * no puede traer miles de filas y competir con la facturacion de las filiales. Los clientes piden
 * hoy hasta 100 (paginadores del desktop) y 200 (subfamilias en la PWA). El recorte queda en el log
 * para detectar quien lo pide. Si el cliente manda valores validos dentro del tope, se respetan.
 */
public final class Paginacion {

    private static final Logger log = LoggerFactory.getLogger(Paginacion.class);

    public static final int SIZE_POR_DEFECTO = 15;
    public static final int SIZE_MAXIMO = 500;

    private Paginacion() {
    }

    public static Pageable of(Integer page, Integer size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? size : SIZE_POR_DEFECTO;
        if (s > SIZE_MAXIMO) {
            log.warn("Paginacion: se pidio size={} y se recorta a {} (#582)", s, SIZE_MAXIMO);
            s = SIZE_MAXIMO;
        }
        return PageRequest.of(p, s, sort);
    }
}
