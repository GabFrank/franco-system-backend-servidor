package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica el criterio de "caja realmente abierta" contra la DB dev real.
 *
 * El bug: el buscador de solicitante traia 4 cajeros de la sucursal 8 cuando habia una sola caja
 * abierta. La causa era usar activo = true, que solo dice que la caja nunca se cerro -- en la base
 * hay cajas de 2023 y 2024 que quedaron asi. El criterio nuevo se queda con la ultima caja de cada
 * maletin.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.caja=true.
 * Es de solo lectura y @Transactional, no ensucia nada.
 *
 * Correr:  ./mvnw -Dit.caja=true -Dtest=CajerosConCajaAbiertaIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.caja", matches = "true")
class CajerosConCajaAbiertaIT {

    private static final Long SUCURSAL_CON_UNA_CAJA_ABIERTA = 8L;

    @Autowired
    private PdvCajaService service;

    @Test
    @DisplayName("el JPQL es valido y corre contra la DB")
    void laQueryEjecuta() {
        List<Usuario> cajeros = service.findCajerosConCajaAbiertaBySucursalId(SUCURSAL_CON_UNA_CAJA_ABIERTA);
        assertTrue(cajeros != null, "la query no devolvio nada, ni siquiera una lista vacia");
    }

    @Test
    @DisplayName("el criterio estricto nunca agrega cajeros que activo = true no tenga")
    void esSubconjuntoDelCriterioViejo() {
        Set<Long> estrictos = service.findCajerosConCajaAbiertaBySucursalId(SUCURSAL_CON_UNA_CAJA_ABIERTA)
                .stream().map(Usuario::getId).collect(Collectors.toSet());
        Set<Long> porActivo = service.findActiveBySucursalId(SUCURSAL_CON_UNA_CAJA_ABIERTA)
                .stream().map(PdvCaja::getUsuario).filter(u -> u != null).map(Usuario::getId)
                .collect(Collectors.toSet());

        assumeTrue(!porActivo.isEmpty(), "la sucursal no tiene ninguna caja con activo = true");
        assertTrue(porActivo.containsAll(estrictos),
                "el criterio estricto devolvio cajeros que activo = true no tiene: " + estrictos + " vs " + porActivo);
    }

    @Test
    @DisplayName("descarta las cajas abandonadas que no son la ultima de su maletin")
    void descartaLasAbandonadas() {
        List<PdvCaja> porActivo = service.findActiveBySucursalId(SUCURSAL_CON_UNA_CAJA_ABIERTA);
        assumeTrue(porActivo.size() > 1, "la sucursal no tiene cajas abandonadas para descartar");

        Set<Long> estrictos = service.findCajerosConCajaAbiertaBySucursalId(SUCURSAL_CON_UNA_CAJA_ABIERTA)
                .stream().map(Usuario::getId).collect(Collectors.toSet());

        // La ultima caja abierta de la sucursal es, por definicion, la ultima de su maletin.
        PdvCaja masReciente = porActivo.stream()
                .filter(c -> c.getFechaApertura() != null)
                .max((a, b) -> a.getFechaApertura().compareTo(b.getFechaApertura()))
                .orElse(null);
        assumeTrue(masReciente != null && masReciente.getUsuario() != null,
                "no hay ninguna caja con fecha de apertura y usuario");

        assertTrue(estrictos.contains(masReciente.getUsuario().getId()),
                "se perdio el cajero de la caja abierta mas reciente (caja " + masReciente.getId() + ")");
        assertFalse(estrictos.size() >= porActivo.size(),
                "no se descarto ninguna caja abandonada: " + estrictos.size() + " cajeros de " + porActivo.size() + " cajas");
    }
}
