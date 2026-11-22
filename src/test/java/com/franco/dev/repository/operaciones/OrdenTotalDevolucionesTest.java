package com.franco.dev.repository.operaciones;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tanda de devoluciones de #674: cada consulta paginada (o con tope) termina su orden en una
 * clave unica. Corre en CI, sin base; el recorrido real esta en {@link OrdenTotalDevolucionesIT}.
 */
class OrdenTotalDevolucionesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "DevolucionRepository        | findByFilters  | ORDER BY d.fecha DESC, d.id DESC",
            "ColectaDevolucionRepository | findByFilters  | ORDER BY c.fecha DESC, c.id DESC",
            "RetiroDevolucionRepository  | findByFilters  | ORDER BY r.fecha DESC, r.id DESC",
            "DevolucionItemRepository    | topProductos   | ORDER BY SUM(di.cantidad) DESC, p.id ASC",
            "DevolucionItemRepository    | topMotivos     | ORDER BY COUNT(di) DESC, m.id ASC",
            "DevolucionItemRepository    | topProveedores | ORDER BY COUNT(DISTINCT d.id) DESC, prov.id ASC"
    })
    void elOrdenTerminaEnUnaClaveUnica(String repo, String metodo, String final_) throws Exception {
        List<String> consultas = Arrays.stream(Class.forName("com.franco.dev.repository.operaciones." + repo).getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertFalse(consultas.isEmpty(), metodo);
        consultas.forEach(q -> assertTrue(q.endsWith(final_), metodo + ": " + q));
    }
}
