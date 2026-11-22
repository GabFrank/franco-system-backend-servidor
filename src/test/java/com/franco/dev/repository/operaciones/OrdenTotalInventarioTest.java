package com.franco.dev.repository.operaciones;

import com.franco.dev.graphql.operaciones.InventarioProductoItemGraphQL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tanda de inventario de #674: cada consulta paginada termina su orden en una clave unica.
 * Corre en CI, sin base; el recorrido real de las paginas esta en {@link OrdenTotalInventarioIT}.
 */
class OrdenTotalInventarioTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "InventarioProductoItemRepository | findProductosVencidos              | ORDER BY i.vencimiento DESC, i.id DESC",
            "InventarioProductoItemRepository | findProductosVencidosConFecha      | ORDER BY i.vencimiento DESC, i.id DESC",
            "InventarioProductoItemRepository | findProductosProximosAVencer       | ORDER BY i.vencimiento DESC, i.id DESC",
            "InventarioProductoItemRepository | findItemsDeInventariosAnteriores   | ORDER BY ipi.vencimiento DESC, ipi.id DESC LIMIT :limit OFFSET :offset",
            "InventarioProductoItemRepository | findItemsDeInventariosAnterioresSoloSucursal | ORDER BY ipi.vencimiento DESC, ipi.id DESC LIMIT :limit OFFSET :offset",
            "InventarioRepository             | findByUsuarioIdOrderByAbiertoPrimero    | i.fechaInicio DESC, i.id DESC",
            "InventarioRepository             | findByUsuarioIdOrderByConcluidoPrimero  | i.fechaInicio DESC, i.id DESC",
            "InventarioRepository             | findByUsuarioIdOrderByCanceladoPrimero  | i.fechaInicio DESC, i.id DESC",
            "MovimientoStockRepository        | findByFilters                      | order by ms.creadoEn, ms.id, ms.sucursalId"
    })
    void elOrdenTerminaEnUnaClaveUnica(String repo, String metodo, String final_) throws Exception {
        Class<?> clase = Class.forName("com.franco.dev.repository.operaciones." + repo);
        List<String> consultas = Arrays.stream(clase.getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertEquals(1, consultas.size(), metodo);
        assertTrue(consultas.get(0).endsWith(final_), metodo + ": " + consultas.get(0));
    }

    @Test
    void laConsultaDerivadaPorVencimientoDesempataPorId() {
        assertTrue(Arrays.stream(InventarioProductoItemRepository.class.getMethods()).anyMatch(
                m -> m.getName().equals("findByInventarioProductoIdAndPresentacionIdOrderByVencimientoDescIdDesc")));
    }

    @Test
    void elPageableDeLosItemsTerminaEnElId() throws Exception {
        Method crear = InventarioProductoItemGraphQL.class.getDeclaredMethod(
                "createPageable", Integer.class, Integer.class, String.class, String.class);
        crear.setAccessible(true);
        InventarioProductoItemGraphQL graphql = new InventarioProductoItemGraphQL();

        Pageable conOrden = (Pageable) crear.invoke(graphql, 0, 20, "vencimiento", "ASC");
        assertEquals(Sort.by(Sort.Order.asc("vencimiento"), Sort.Order.asc("id")), conOrden.getSort());

        Pageable sinOrden = (Pageable) crear.invoke(graphql, 0, 20, null, null);
        assertEquals(Sort.by(Sort.Order.asc("id")), sinOrden.getSort());
    }
}
