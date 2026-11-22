package com.franco.dev.repository.financiero;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre de punta a punta las listas de retiros y gastos de todas las sucursales y verifica que
 * ninguna fila se repita ni se pierda (#674). Solo lee; contra bodega3 local, nunca produccion:
 *
 *   ./mvnw test -Dit.orden=true -Dtest=OrdenTotalCajaIT \
 *       -Dspring.datasource.url=jdbc:postgresql://localhost:5551/bodega3
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional(readOnly = true)
@EnabledIfSystemProperty(named = "it.orden", matches = "true")
class OrdenTotalCajaIT {

    @Autowired private RetiroRepository retiroRepository;
    @Autowired private GastoRepository gastoRepository;

    @Test
    void retirosDeTodasLasSucursales() {
        recorrer(500, p -> retiroRepository.findByAllPage(null, null, null, null, null, p),
                r -> r.getId() + "@" + r.getSucursalId());
    }

    @Test
    void gastosDeTodasLasSucursales() {
        recorrer(500, p -> gastoRepository.findByAllPage(null, null, null, null, null, p),
                g -> g.getId() + "@" + g.getSucursalId());
    }

    private <T> void recorrer(int size, Function<Pageable, Page<T>> consulta, Function<T, String> clave) {
        List<String> filas = new ArrayList<>();
        Page<T> page;
        int n = 0;
        do {
            page = consulta.apply(PageRequest.of(n++, size));
            page.getContent().forEach(x -> filas.add(clave.apply(x)));
        } while (page.hasNext());
        assertTrue(page.getTotalPages() > 1, "hacen falta varias paginas para probar algo: " + page.getTotalElements());
        assertEquals(page.getTotalElements(), filas.size(), "filas recorridas");
        assertEquals(filas.size(), new HashSet<>(filas).size(), "filas repetidas entre paginas");
    }
}
