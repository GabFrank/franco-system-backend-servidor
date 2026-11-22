package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.productos.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Busqueda de productos de a 10 y rankings de ventas con tope (#674): sin repetidos entre paginas,
 * y el top chico es el comienzo exacto del top grande. Solo lee; contra bodega3, nunca produccion:
 *
 *   ./mvnw test -Dit.orden=true -Dtest=OrdenTotalVentasIT \
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
class OrdenTotalVentasIT {

    private static final LocalDateTime FIN = LocalDateTime.of(2026, 11, 20, 0, 0);
    private static final LocalDateTime INICIO = FIN.minusDays(30);

    @Autowired private ProductoRepository productoRepository;
    @Autowired private VentaRepository ventaRepository;

    @Test
    void busquedaDeProductosDeA10() {
        List<Long> ids = new ArrayList<>();
        List<Producto> pagina;
        int offset = 0;
        do {
            pagina = productoRepository.findbyAll("COCA", offset, "1", "false");
            pagina.forEach(p -> ids.add(p.getId()));
            offset += 10;
        } while (pagina.size() == 10);
        assertTrue(offset > 20, "hacen falta varias paginas: " + ids.size());
        assertEquals(ids.size(), new HashSet<>(ids).size(), "productos repetidos entre paginas");
    }

    @Test
    void elTopChicoDeVentasPorFuncionarioEsElComienzoDelGrande() {
        List<Object> top5 = ventaRepository.getVentasPorFuncionarioAll(INICIO, FIN, PageRequest.of(0, 5))
                .stream().map(r -> r[0]).collect(Collectors.toList());
        List<Object> top50 = ventaRepository.getVentasPorFuncionarioAll(INICIO, FIN, PageRequest.of(0, 50))
                .stream().map(r -> r[0]).collect(Collectors.toList());
        assertTrue(top50.size() > 5, "hacen falta datos: " + top50.size());
        assertEquals(top5, top50.subList(0, 5));
    }
}
