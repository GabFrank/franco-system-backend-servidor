package com.franco.dev.utilitarios;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdUtilsTest {

    @Test
    void convierteIntegerALong() {
        // Los campos [Int] del schema llegan como Integer aunque el resolver
        // los reciba tipados como List<Long>: usarlos como Long tira ClassCastException.
        List<?> desdeGraphQL = Arrays.asList(1, 2, 3);

        List<Long> ids = IdUtils.toLongList(desdeGraphQL);

        assertEquals(Arrays.asList(1L, 2L, 3L), ids);
    }

    @Test
    void convierteStringALong() {
        assertEquals(Arrays.asList(10L, 20L), IdUtils.toLongList(Arrays.asList("10", " 20 ")));
    }

    @Test
    void mantieneLosLong() {
        assertEquals(Collections.singletonList(7L), IdUtils.toLongList(Collections.singletonList(7L)));
    }

    @Test
    void devuelveNullSiNoHayFiltro() {
        assertNull(IdUtils.toLongList(null));
        assertNull(IdUtils.toLongList(Collections.emptyList()));
    }

    @Test
    void ignoraNullsYValoresNoNumericos() {
        assertEquals(Collections.singletonList(5L), IdUtils.toLongList(Arrays.asList(null, "abc", 5)));
    }

    @Test
    void devuelveNullSiNingunIdEsValido() {
        assertNull(IdUtils.toLongList(Arrays.asList("abc", "-")));
    }

    @Test
    void toLongAceptaDistintosTipos() {
        assertEquals(4L, IdUtils.toLong(4));
        assertEquals(4L, IdUtils.toLong(4L));
        assertEquals(4L, IdUtils.toLong("4"));
        assertNull(IdUtils.toLong("x"));
        assertNull(IdUtils.toLong(null));
    }
}
