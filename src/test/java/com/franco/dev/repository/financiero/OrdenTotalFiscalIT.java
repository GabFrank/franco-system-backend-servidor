package com.franco.dev.repository.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.service.financiero.FacturaLegalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre de punta a punta listas de documentos fiscales de varias sucursales y verifica que
 * ninguna fila se repita ni se pierda (#674). En bodega3, el 15/11/2026 hay 5.508 facturas y 374
 * comparten el id con una factura de otra sucursal.
 *
 * Solo lee; contra bodega3 local, nunca produccion:
 *
 *   ./mvnw test -Dit.orden=true -Dtest=OrdenTotalFiscalIT \
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
class OrdenTotalFiscalIT {

    @Autowired private FacturaLegalService facturaLegalService;
    @Autowired private FacturaLegalRepository facturaLegalRepository;
    @Autowired private LoteDERepository loteDERepository;
    @Autowired private SucursalRepository sucursalRepository;

    private List<Long> todasLasSucursales() {
        return sucursalRepository.findAll().stream().map(Sucursal::getId).collect(Collectors.toList());
    }

    @Test
    void facturasDeUnDiaConFechas() {
        List<Long> suc = todasLasSucursales();
        recorrer(500, p -> facturaLegalService.findByAll(p.getPageNumber(), p.getPageSize(),
                "2026-11-15 00:00:00", "2026-11-15 23:59:59", suc, null, null, null, null, null, null, null, null),
                f -> f.getId() + "@" + f.getSucursalId());
    }

    @Test
    void facturasDeUnDiaPorCreacion() {
        List<Long> suc = todasLasSucursales();
        recorrer(500, p -> facturaLegalRepository.findByCreadoEnBetweenAndSucursalId(
                LocalDateTime.of(2026, 11, 15, 0, 0), LocalDateTime.of(2026, 11, 15, 23, 59, 59), suc, null, null, p),
                f -> f.getId() + "@" + f.getSucursalId());
    }

    @Test
    void lotesDeUnMes() {
        recorrer(500, p -> loteDERepository.findByEstadoOrFechaProcesadoBetween(null,
                LocalDateTime.of(2026, 10, 15, 0, 0), LocalDateTime.of(2026, 11, 15, 0, 0), p),
                l -> l.getId() + "@" + l.getSucursalId());
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
