package com.franco.dev.repository.operaciones;

import com.franco.dev.graphql.operaciones.InventarioProductoItemGraphQL;
import com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.ProductosVencidosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre de punta a punta las consultas paginadas de inventario y verifica que ninguna fila se
 * repita ni se pierda entre paginas (#674). En bodega3 hay 65.655 items vencidos con solo 1.402
 * fechas distintas: con el orden viejo, 20 paginas de 500 daban 9.736 items distintos de 10.000.
 *
 * Solo lee. Contra una base con datos reales (bodega3 local, nunca produccion):
 *
 *   ./mvnw test -Dit.orden=true -Dtest=OrdenTotalInventarioIT \
 *       -Dspring.datasource.url=jdbc:postgresql://localhost:5551/bodega3
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@EnabledIfSystemProperty(named = "it.orden", matches = "true")
class OrdenTotalInventarioIT {

    /**
     * El Pageable sale del mismo createPageable que usa el resolver, con el orden por vencimiento
     * que piden el desktop y las queries de vencidos: asi el test pasa por el arreglo real.
     */
    private static Pageable comoElResolver(Pageable p) {
        try {
            Method crear = InventarioProductoItemGraphQL.class.getDeclaredMethod(
                    "createPageable", Integer.class, Integer.class, String.class, String.class);
            crear.setAccessible(true);
            return (Pageable) crear.invoke(new InventarioProductoItemGraphQL(), p.getPageNumber(), p.getPageSize(),
                    "vencimiento", "ASC");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private InventarioProductoItemService itemService;

    @Autowired
    private InventarioProductoItemRepository itemRepository;

    @Autowired
    private ProductosVencidosService vencidosService;

    @Autowired
    private MovimientoStockRepository movimientoRepository;

    @Test
    void itemsDeInventarioConFiltros() {
        LocalDateTime fin = LocalDateTime.now();
        recorrer(500, p -> itemService.findAllWithFilters(null, null, null, fin.minusDays(180), fin,
                null, null, null, null, null, null, null, comoElResolver(p)),
                i -> String.valueOf(i.getId()));
    }

    @Test
    void itemsVencidosDelUltimoInventario() {
        recorrer(5, p -> itemRepository.findProductosVencidos(null, null, null, null, null,
                comoElResolver(p)),
                i -> String.valueOf(i.getId()));
    }

    @Test
    void productosVencidosDeTodasLasFuentes() {
        recorrer(50, p -> vencidosService.buscarProductosVencidos(null, null, null, null, null, null, null, null,
                null, comoElResolver(p)),
                (ProductoVencidoViewDTO v) -> v.getSucursalId() + "|" + v.getPresentacionId() + "|"
                        + v.getVencimiento().toLocalDate());
    }

    @Test
    void movimientosDeStockDeLaUltimaSemana() {
        LocalDateTime fin = LocalDateTime.now();
        recorrer(500, p -> movimientoRepository.findByFilters(fin.minusDays(7), fin, null, null, null, null, p),
                m -> m.getId() + "@" + m.getSucursalId());
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
