package com.franco.dev.repository.activos;

import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.repository.financiero.EnteCuotaRepository;
import com.franco.dev.repository.financiero.EnteFinancieroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Recorre de a paginas chicas los catalogos paginados de la tanda de #674 y verifica que ninguna
 * fila se repita ni se pierda. Solo lee; contra bodega3 local, nunca produccion:
 *
 *   ./mvnw test -Dit.orden=true -Dtest=OrdenTotalCatalogosIT \
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
class OrdenTotalCatalogosIT {

    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private ModeloRepository modeloRepository;
    @Autowired private TipoVehiculoRepository tipoVehiculoRepository;
    @Autowired private EnteFinancieroRepository enteFinancieroRepository;
    @Autowired private EnteCuotaRepository enteCuotaRepository;

    @Test
    void sucursalesComoLasPideElResolver() {
        recorrer(3, p -> sucursalRepository.findAll(PageRequest.of(p.getPageNumber(), p.getPageSize(), Sort.by("id"))),
                s -> String.valueOf(s.getId()));
    }

    @Test
    void modelos() {
        recorrer(3, p -> modeloRepository.findByAllWithPage("%", p), m -> String.valueOf(m.getId()));
    }

    @Test
    void tiposDeVehiculo() {
        recorrer(2, p -> tipoVehiculoRepository.findByAllWithPage("%", p), t -> String.valueOf(t.getId()));
    }

    @Test
    void entesFinancieros() {
        recorrer(2, p -> enteFinancieroRepository.findByAllWithPage("%", p), e -> String.valueOf(e.getId()));
    }

    @Test
    void cuotasDeEntes() {
        recorrer(5, p -> enteCuotaRepository.findAllByEnteFinancieroId(null, p), c -> String.valueOf(c.getId()));
    }

    private <T> void recorrer(int size, Function<Pageable, Page<T>> consulta, Function<T, String> clave) {
        List<String> filas = new ArrayList<>();
        Page<T> page;
        int n = 0;
        do {
            page = consulta.apply(PageRequest.of(n++, size));
            page.getContent().forEach(x -> filas.add(clave.apply(x)));
        } while (page.hasNext());
        assertEquals(page.getTotalElements(), filas.size(), "filas recorridas");
        assertEquals(filas.size(), new HashSet<>(filas).size(), "filas repetidas entre paginas");
    }
}
