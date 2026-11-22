package com.franco.dev.repository.operaciones;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre de a paginas chicas las listas y los tops de devoluciones y verifica que ninguna fila se
 * repita ni se pierda (#674). Solo lee; contra bodega3 local, nunca produccion:
 *
 *   ./mvnw test -Dit.orden=true -Dtest=OrdenTotalDevolucionesIT \
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
class OrdenTotalDevolucionesIT {

    private static final LocalDateTime DESDE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime HASTA = LocalDateTime.of(2100, 1, 1, 0, 0);

    @Autowired
    private DevolucionRepository devolucionRepository;

    @Autowired
    private DevolucionItemRepository itemRepository;

    @Test
    void devolucionesConFiltros() {
        recorrer(2, p -> devolucionRepository.findByFilters(null, null, null, DESDE, HASTA, null, p).getContent(),
                d -> String.valueOf(d.getId()));
    }

    @Test
    void topProductosDevueltos() {
        recorrer(10, p -> itemRepository.topProductos(DESDE, HASTA, null, p), t -> String.valueOf(t.getProductoId()));
    }

    @Test
    void topMotivos() {
        recorrer(1, p -> itemRepository.topMotivos(DESDE, HASTA, null, p), t -> String.valueOf(t.getMotivoId()));
    }

    @Test
    void topProveedores() {
        recorrer(2, p -> itemRepository.topProveedores(DESDE, HASTA, null, p), t -> String.valueOf(t.getProveedorId()));
    }

    /** Pide paginas hasta que una venga corta; con un orden total no hay repetidos entre paginas. */
    private <T> void recorrer(int size, Function<Pageable, List<T>> consulta, Function<T, String> clave) {
        List<String> filas = new ArrayList<>();
        List<T> pagina;
        int n = 0;
        do {
            pagina = consulta.apply(PageRequest.of(n++, size));
            pagina.forEach(x -> filas.add(clave.apply(x)));
        } while (pagina.size() == size);
        assertTrue(n > 2, "hacen falta varias paginas para probar algo: " + filas.size());
        assertEquals(filas.size(), new HashSet<>(filas).size(), "filas repetidas entre paginas");
    }
}
