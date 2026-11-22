package com.franco.dev.service;

import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.service.financiero.RetiroService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre de a 500 la query generica retiros(page, size) -> CrudService.findAll (#674). En bodega3
 * hay 58.969 retiros y 5.468 ids repetidos entre sucursales. Solo lee; contra bodega3, nunca prod:
 *
 *   ./mvnw test -Dit.orden=true -Dtest=CrudServiceOrdenIT \
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
class CrudServiceOrdenIT {

    @Autowired private RetiroService retiroService;

    @Test
    void retirosPorElFindAllGenerico() {
        List<String> filas = new ArrayList<>();
        List<Retiro> pagina;
        int n = 0;
        do {
            pagina = retiroService.findAll(PageRequest.of(n++, 500));
            pagina.forEach(r -> filas.add(r.getId() + "@" + r.getSucursalId()));
        } while (pagina.size() == 500);
        assertTrue(n > 10, "hacen falta varias paginas: " + filas.size());
        assertEquals(filas.size(), new HashSet<>(filas).size(), "filas repetidas entre paginas");
    }
}
