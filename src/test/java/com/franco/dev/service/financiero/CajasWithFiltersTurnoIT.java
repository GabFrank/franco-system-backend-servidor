package com.franco.dev.service.financiero;

import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.domain.financiero.PdvCaja;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Filtro por turno de la lista de Cajas: cajasWithFilters con turno, cortes de bodega (default).
 *
 * NO corre en CI (no hay DB): se activa con -Dit.caja=true. Solo lectura y @Transactional.
 * Correrlo también con -Duser.timezone=UTC: la hora tiene que salir igual (se convierte en la query).
 *
 * Correr:  ./mvnw -Dit.caja=true -Dtest=CajasWithFiltersTurnoIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.caja", matches = "true")
class CajasWithFiltersTurnoIT {

    // En la base dev tiene cajas de los tres turnos.
    private static final Long SUCURSAL = 3L;

    @Autowired
    private PdvCajaService service;

    private Page<PdvCaja> buscar(Turno turno, int size) {
        return service.findAllWithFilters(null, null, null, null, null, null, SUCURSAL, null, null, turno, PageRequest.of(0, size));
    }

    @Autowired
    private JdbcTemplate jdbc;

    /** Ventanas de bodega por SQL, con la hora en Asunción explícita: no depende de la zona de la sesión.
     *  La lista hace join con maletín y usuario: una caja sin alguno de los dos nunca sale. */
    private static final Map<Turno, String> VENTANA = Map.of(
            Turno.DIA, "h >= time '07:00' and h < time '15:30'",
            Turno.NOCHE, "h >= time '15:30' and h < time '23:00'",
            Turno.MADRUGADA, "(h >= time '23:00' or h < time '07:00')");

    @Test
    @DisplayName("cada turno trae exactamente las cajas que da el SQL con la hora de Asunción")
    void mismasCajasQueElSql() {
        for (Turno turno : Turno.values()) {
            Set<Long> esperadas = new HashSet<>(jdbc.queryForList(
                    "select id from (select id, cast(timezone('America/Asuncion', creado_en) as time) h " +
                            "from financiero.pdv_caja where sucursal_id = ? and maletin_id is not null and usuario_id is not null) c where " + VENTANA.get(turno),
                    Long.class, SUCURSAL));
            Set<Long> obtenidas = buscar(turno, 10000).getContent().stream().map(PdvCaja::getId).collect(Collectors.toSet());
            assertFalse(esperadas.isEmpty(), "la sucursal no tiene cajas de " + turno);
            assertEquals(esperadas, obtenidas, turno.toString());
        }
    }

    @Test
    @DisplayName("los tres turnos suman todas las cajas")
    void particion() {
        long todas = buscar(null, 1).getTotalElements();
        long suma = 0;
        for (Turno turno : Turno.values()) suma += buscar(turno, 1).getTotalElements();
        assertEquals(todas, suma);
    }
}
