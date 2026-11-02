package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filtro Abiertas/Cerradas de la lista de Cajas del desktop (#599): cajasWithFilters con activo.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.caja=true. Solo lectura y @Transactional.
 *
 * Correr:  ./mvnw -Dit.caja=true -Dtest=CajasWithFiltersActivoIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.caja", matches = "true")
class CajasWithFiltersActivoIT {

    // En la base dev tiene cajas abiertas y cerradas.
    private static final Long SUCURSAL = 3L;

    @Autowired
    private PdvCajaService service;

    private Page<PdvCaja> buscar(Boolean activo, int size) {
        return service.findAllWithFilters(null, null, null, null, null, null, SUCURSAL, null, activo, PageRequest.of(0, size));
    }

    @Test
    @DisplayName("Abiertas trae solo cajas activas")
    void abiertas() {
        Page<PdvCaja> page = buscar(true, 200);
        assertFalse(page.isEmpty(), "la sucursal no tiene cajas abiertas");
        assertTrue(page.getContent().stream().allMatch(c -> Boolean.TRUE.equals(c.getActivo())));
    }

    @Test
    @DisplayName("Cerradas trae solo cajas no activas")
    void cerradas() {
        Page<PdvCaja> page = buscar(false, 200);
        assertFalse(page.isEmpty(), "la sucursal no tiene cajas cerradas");
        assertTrue(page.getContent().stream().allMatch(c -> Boolean.FALSE.equals(c.getActivo())));
    }

    @Test
    @DisplayName("sin activo trae todas: abiertas + cerradas")
    void todas() {
        long todas = buscar(null, 1).getTotalElements();
        assertEquals(todas, buscar(true, 1).getTotalElements() + buscar(false, 1).getTotalElements());
    }
}
