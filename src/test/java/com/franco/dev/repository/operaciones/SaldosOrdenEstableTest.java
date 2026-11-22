package com.franco.dev.repository.operaciones;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los listados de saldos se recorren de a paginas y miles de filas empatan en la cantidad: si el
 * ORDER BY no termina en una clave unica, PostgreSQL devuelve los empatados distinto en cada
 * pagina y hay productos repetidos y perdidos (#674). Este test no necesita base y corre en CI;
 * el recorrido real de las paginas esta en {@link SaldosPaginadosIT}.
 */
class SaldosOrdenEstableTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "findProductosConCantidadPositiva | ORDER BY SUM(ms.cantidad) DESC, ms.producto.id ASC, ms.sucursalId ASC",
            "findProductosConCantidadNegativa | ORDER BY SUM(ms.cantidad) ASC, ms.producto.id ASC, ms.sucursalId ASC",
            "findProductosFaltantes           | ORDER BY p.descripcion ASC, p.id ASC"
    })
    void elOrdenTerminaEnUnaClaveUnica(String metodo, String ordenEsperado) {
        Method m = Arrays.stream(MovimientoStockRepository.class.getMethods())
                .filter(x -> x.getName().equals(metodo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe " + metodo));
        String jpql = m.getAnnotation(Query.class).value().trim();
        assertTrue(jpql.endsWith(ordenEsperado), metodo + " ordena con: " + jpql.substring(jpql.indexOf("ORDER BY")));
    }
}
