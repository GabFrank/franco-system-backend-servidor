package com.franco.dev.repository.operaciones;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El ingreso del grafico Ingresos vs Gastos es lo cobrado: pagos menos vuelto.
 *
 * Las filas de vuelto son pago=false, vuelto=true, y se guardan con valor negativo.
 * Un filtro "cd.pago = true" en el WHERE las descartaba antes de restarlas, y el
 * grafico mostraba cientos de millones por mes de mas. Hay ademas filas en guaranies
 * con cambio NULL, que valor * cambio sacaba del SUM, y la PK de venta es
 * (id, sucursal_id): contar DISTINCT v.id junta ventas de sucursales distintas.
 */
class VentaRepositoryIngresosPorMesTest {

    private String sqlDe(String nombreMetodo) {
        Method metodo = Arrays.stream(VentaRepository.class.getMethods())
                .filter(m -> m.getName().equals(nombreMetodo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe " + nombreMetodo));
        Query query = metodo.getAnnotation(Query.class);
        assertNotNull(query, nombreMetodo + " debe declarar @Query");
        return query.value().replaceAll("\\s+", " ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ventasPorMes", "ventasPorMesSinSucursal"})
    void lasFilasDeVueltoEntranALaConsulta(String metodo) {
        String sql = sqlDe(metodo);
        assertFalse(sql.contains("AND cd.pago = true AND"),
                "filtrar solo pagos descarta el vuelto antes de restarlo");
        assertTrue(sql.contains("AND (cd.pago = true OR cd.vuelto = true)"),
                "el WHERE tiene que admitir las filas de vuelto");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ventasPorMes", "ventasPorMesSinSucursal"})
    void elVueltoSeRestaSinImportarElSigno(String metodo) {
        String sql = sqlDe(metodo);
        assertTrue(sql.contains("WHEN cd.vuelto = true THEN -ABS(cd.valor * COALESCE(cd.cambio, 1))"),
                "el vuelto se guarda negativo: restarlo sin ABS lo terminaria sumando");
        assertFalse(sql.contains("- (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio"),
                "restar el valor con signo suma el vuelto negativo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ventasPorMes", "ventasPorMesSinSucursal"})
    void unCambioNuloSeTomaComoGuarani(String metodo) {
        String sql = sqlDe(metodo);
        assertTrue(sql.contains("WHEN cd.pago = true THEN cd.valor * COALESCE(cd.cambio, 1)"),
                "valor * NULL saca el pago del SUM");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ventasPorMes", "ventasPorMesSinSucursal"})
    void laCantidadDistingueSucursal(String metodo) {
        String sql = sqlDe(metodo);
        assertTrue(sql.contains("COUNT(DISTINCT (v.id, v.sucursal_id))"),
                "la PK de venta es (id, sucursal_id)");
    }
}
