package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.domain.activos.enums.TipoEnte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #774: filtro por bien en «Vehículo por sucursal», contra la base dev (bodega3 local).
 *
 * NO corre en CI (no hay DB): se activa con -Dit.ente=true. Solo lectura y @Transactional.
 *
 * Correr:  ./mvnw -Dit.ente=true -Dtest=EnteSucursalFiltroEnteIdIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.ente", matches = "true")
class EnteSucursalFiltroEnteIdIT {

    // En bodega3: ente 3 = «VEHÍCULO - BFP517», el único vehículo con asignación a sucursal.
    private static final Long ENTE_CON_ASIGNACION = 3L;
    // Ente 1 = «VEHÍCULO - BNL515», sin asignaciones.
    private static final Long ENTE_SIN_ASIGNACION = 1L;

    @Autowired
    private EnteSucursalService service;

    @Test
    @DisplayName("con enteId trae solo las asignaciones de ese bien")
    void filtraPorEnte() {
        Page<EnteSucursal> page = service.findAllWithFilters(null, null, TipoEnte.VEHICULO, null, ENTE_CON_ASIGNACION, 0, 50);
        assertFalse(page.isEmpty(), "el ente 3 no tiene asignaciones en la base dev");
        assertTrue(page.getContent().stream().allMatch(es -> ENTE_CON_ASIGNACION.equals(es.getEnte().getId())));
    }

    @Test
    @DisplayName("un bien sin asignaciones da la lista vacía")
    void enteSinAsignaciones() {
        assertEquals(0, service.findAllWithFilters(null, null, TipoEnte.VEHICULO, null, ENTE_SIN_ASIGNACION, 0, 50).getTotalElements());
    }

    @Test
    @DisplayName("la descripción mostrada («Chapa: …») como texto no encuentra nada: por eso se filtra por id")
    void descripcionMostradaNoCoincide() {
        assertEquals(0, service.findAllWithFilters("Chapa: BFP517", null, TipoEnte.VEHICULO, null, null, 0, 50).getTotalElements());
    }

    @Test
    @DisplayName("sin enteId no filtra por bien")
    void sinEnteId() {
        long todas = service.findAllWithFilters(null, null, TipoEnte.VEHICULO, null, null, 0, 1).getTotalElements();
        long delEnte = service.findAllWithFilters(null, null, TipoEnte.VEHICULO, null, ENTE_CON_ASIGNACION, 0, 1).getTotalElements();
        assertTrue(todas >= delEnte);
    }
}
