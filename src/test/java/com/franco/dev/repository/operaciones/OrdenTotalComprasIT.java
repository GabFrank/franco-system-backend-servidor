package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre de punta a punta consultas paginadas de compras y verifica que ninguna fila se repita
 * ni se pierda entre paginas (#674). En bodega3 hay 8.448 etapas COMPLETADA con 698 grupos que
 * comparten creado_en, y notas con varios items del mismo producto (misma descripcion).
 *
 * Solo lee. Contra una base con datos reales (bodega3 local, nunca produccion):
 *
 *   ./mvnw test -Dit.orden=true -Dtest=OrdenTotalComprasIT \
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
class OrdenTotalComprasIT {

    /** La nota de bodega3 con mas items que empatan en la descripcion del producto (9 de 28). */
    private static final long NOTA_CON_EMPATES = 1356L;
    /** El producto de bodega3 con mas costos (2.261). */
    private static final long PRODUCTO_CON_MAS_COSTOS = 807L;

    @Autowired
    private ProcesoEtapaRepository etapaRepository;

    @Autowired
    private NotaRecepcionItemRepository notaItemRepository;

    @Autowired
    private CostosPorProductoRepository costoRepository;

    @Test
    void etapasPorEstado() {
        recorrer(500, p -> etapaRepository.findByEstadoEtapa(ProcesoEtapaEstado.COMPLETADA, p),
                e -> String.valueOf(e.getId()));
    }

    @Test
    void itemsDeUnaNotaPorDescripcion() {
        recorrer(3, p -> notaItemRepository.findItemsByNotaIdPaginados(NOTA_CON_EMPATES, p),
                i -> String.valueOf(i.getId()));
    }

    @Test
    void costosDeUnProductoComoLosPideElResolver() {
        recorrer(100, p -> costoRepository.findByProductoId(PRODUCTO_CON_MAS_COSTOS,
                PageRequest.of(p.getPageNumber(), p.getPageSize(), Sort.by(Sort.Direction.DESC, "id"))),
                c -> String.valueOf(c.getId()));
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
