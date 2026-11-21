package com.franco.dev.service.configuraciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/** El estado anterior se copia ANTES de guardar: si no, el save pisa la misma instancia y no queda qué comparar. */
class ModificacionServiceCopiaTest {

    public static class Base {
        private Long id;
        public Long getId() { return id; }
    }

    public static class Item extends Base {
        private Double cantidad;
        private String descripcion;
    }

    @Test
    void copiaLosCamposDeLaClaseYDeSuPadre() {
        Item original = new Item();
        ((Base) original).id = 5L;
        original.cantidad = 10.0;
        original.descripcion = "UNIDAD";

        Item copia = ModificacionService.copiaParaAuditoria(original);
        // Lo que hace el save sobre la instancia gestionada
        original.cantidad = 12.0;

        assertNotSame(original, copia);
        assertEquals(5L, copia.getId());
        assertEquals(10.0, copia.cantidad);
        assertEquals("UNIDAD", copia.descripcion);
    }

    @Test
    void sinEntidadNoHayCopia() {
        assertNull(ModificacionService.copiaParaAuditoria(null));
    }
}
