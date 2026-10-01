package com.franco.dev.repository.operaciones;

import com.franco.dev.repository.productos.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los reportes de lucro valúan una venta sin costo con el costo medio del producto. Antes caían al
 * último precio de compra: una sola compra promocional (660 latas a 42,5 Gs, 17/09/2026) bajaba el
 * costo de todo lo vendido. Las consultas no se ejecutan en CI; este test fija su texto.
 */
class LucroRespaldoCostoMedioTest {

    private String sql(Class<?> repositorio, String metodo) {
        Method m = Arrays.stream(repositorio.getMethods())
                .filter(x -> x.getName().equals(metodo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe " + metodo));
        Query query = m.getAnnotation(Query.class);
        assertNotNull(query, metodo + " debe declarar @Query");
        return query.value().replaceAll("\\s+", " ");
    }

    @Test
    void lucroPorProductoUsaElCostoMedioComoRespaldo() {
        String sql = sql(ProductoRepository.class, "findLucroPorProducto");
        assertTrue(sql.contains("COALESCE(vi.precioCosto, NULLIF(cpp.costoMedio, 0), cpp.ultimoPrecioCompra, 0)"),
                "sin costo en el ítem, primero el costo medio y recién después el último precio de compra");
    }

    @Test
    void lucroPorFuncionarioUsaElCostoMedioComoRespaldo() {
        String sql = sql(VentaItemRepository.class, "findLucroPorFuncionarioNative");
        assertTrue(sql.contains("COALESCE(vi.costo_unitario, NULLIF(cpp.costo_medio, 0), cpp.ultimo_precio_compra, 0)"),
                "sin costo en el ítem, primero el costo medio y recién después el último precio de compra");
        assertTrue(sql.contains("SELECT DISTINCT ON (producto_id) producto_id, ultimo_precio_compra, costo_medio "),
                "la subconsulta del último costo tiene que traer costo_medio o la consulta falla en runtime");
    }
}
