package com.franco.dev.utilitarios;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pageable para resolvers que reciben page/size como Integer opcionales (#582).
 *
 * Con PageRequest.of(page, size) directo, un page o size nulo tira NullPointerException y uno
 * negativo o cero tira IllegalArgumentException. Aca se reemplazan por la primera pagina de
 * {@link #SIZE_POR_DEFECTO}, el mismo valor que ya usaban ventasPorCajaId y ventasGenericFilter.
 * Si el cliente manda valores validos, se respetan tal cual.
 */
public final class Paginacion {

    public static final int SIZE_POR_DEFECTO = 15;

    private Paginacion() {
    }

    public static Pageable of(Integer page, Integer size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? size : SIZE_POR_DEFECTO;
        return PageRequest.of(p, s, sort);
    }
}
