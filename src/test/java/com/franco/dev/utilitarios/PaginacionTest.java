package com.franco.dev.utilitarios;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #582: varias consultas hacian PageRequest.of(page, size) con Integer que pueden llegar nulos
 * (el schema no les pone valor por defecto) y fallaban con NullPointerException.
 */
class PaginacionTest {

    @Test
    void conPageYSizeLosRespeta() {
        Pageable p = Paginacion.of(3, 50);
        assertEquals(3, p.getPageNumber());
        assertEquals(50, p.getPageSize());
    }

    @Test
    void sinPageYSizeEsLaPrimeraPaginaDe15() {
        Pageable p = Paginacion.of(null, null);
        assertEquals(0, p.getPageNumber());
        assertEquals(Paginacion.SIZE_POR_DEFECTO, p.getPageSize());
        assertEquals(15, Paginacion.SIZE_POR_DEFECTO);
    }

    @Test
    void pageSinSizeUsaElSizePorDefecto() {
        Pageable p = Paginacion.of(2, null);
        assertEquals(2, p.getPageNumber());
        assertEquals(15, p.getPageSize());
    }

    @Test
    void sizeSinPageEsLaPrimeraPagina() {
        Pageable p = Paginacion.of(null, 40);
        assertEquals(0, p.getPageNumber());
        assertEquals(40, p.getPageSize());
    }

    @Test
    void valoresInvalidosNoRompen() {
        // PageRequest.of tira IllegalArgumentException con page < 0 o size < 1
        Pageable p = Paginacion.of(-1, 0);
        assertEquals(0, p.getPageNumber());
        assertEquals(15, p.getPageSize());
    }

    // Punto 3 de #582: tope de size del lado del servidor

    @Test
    void sizeMayorAlTopeSeRecortaAlTope() {
        Pageable p = Paginacion.of(2, 100000);
        assertEquals(2, p.getPageNumber());
        assertEquals(Paginacion.SIZE_MAXIMO, p.getPageSize());
        assertEquals(500, Paginacion.SIZE_MAXIMO);
    }

    @Test
    void sizeIgualAlTopeSeRespeta() {
        assertEquals(500, Paginacion.of(0, 500).getPageSize());
    }

    @Test
    void sizesQuePidenHoyLosClientesNoCambian() {
        // paginadores del desktop (15..100) y subfamilias de la PWA (200)
        for (int size : new int[]{10, 15, 25, 50, 100, 200}) {
            assertEquals(size, Paginacion.of(0, size).getPageSize());
        }
    }

    @Test
    void elTopeConservaElOrden() {
        Pageable p = Paginacion.of(0, 1000, Sort.by("persona.nombre"));
        assertEquals(500, p.getPageSize());
        assertEquals(Sort.by("persona.nombre"), p.getSort());
    }

    @Test
    void conservaElOrden() {
        Pageable p = Paginacion.of(null, null, Sort.by("id").ascending());
        assertEquals(Sort.by("id").ascending(), p.getSort());
    }
}
