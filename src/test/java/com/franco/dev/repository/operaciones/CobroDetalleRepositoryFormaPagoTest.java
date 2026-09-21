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
 * El grafico Formas de Pago tiene que cerrar con Ingresos vs Gastos.
 *
 * El total por forma de pago se muestra en guaranies: los cobros en reales y dolares
 * se convierten con su cambio (NULL en filas viejas en guaranies). El desglose por
 * moneda se muestra en la moneda original, asi que ahi no se convierte. En los dos,
 * el vuelto se resta con ABS porque se guarda negativo (y unas filas viejas en
 * positivo), la cantidad cuenta ventas por (id, sucursal_id) y se excluyen los cobros
 * atipicos, igual que en VentaRepository.ventasPorMes.
 */
class CobroDetalleRepositoryFormaPagoTest {

    private static final String[] TOTALES = {
            "obtenerEstadisticasFormaPago",
            "obtenerEstadisticasFormaPagoPorSucursal",
            "obtenerEstadisticasFormaPagoPorFecha",
            "obtenerEstadisticasFormaPagoPorFechaYSucursal"};

    private String sqlDe(String nombreMetodo) {
        Method metodo = Arrays.stream(CobroDetalleRepository.class.getMethods())
                .filter(m -> m.getName().equals(nombreMetodo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe " + nombreMetodo));
        Query query = metodo.getAnnotation(Query.class);
        assertNotNull(query, nombreMetodo + " debe declarar @Query");
        return query.value().replaceAll("\\s+", " ");
    }

    private boolean esTotal(String metodo) {
        return Arrays.asList(TOTALES).contains(metodo);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "obtenerEstadisticasFormaPago",
            "obtenerEstadisticasFormaPagoPorSucursal",
            "obtenerEstadisticasFormaPagoPorFecha",
            "obtenerEstadisticasFormaPagoPorFechaYSucursal"})
    void elTotalSeConvierteAGuaranies(String metodo) {
        String sql = sqlDe(metodo);
        assertTrue(sql.contains("WHEN cd2.pago = true THEN cd2.valor * COALESCE(cd2.cambio, 1)"),
                "sumar cd.valor sin cambio mezcla reales y dolares con guaranies");
        assertTrue(sql.contains("WHEN cd2.vuelto = true THEN -ABS(cd2.valor * COALESCE(cd2.cambio, 1))"),
                "el vuelto se resta en guaranies y sin importar el signo guardado");
        assertFalse(sql.contains("SUM(cd.valor)"), "el total no puede sumar el valor crudo");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "obtenerDesgloseMonedaFormaPago",
            "obtenerDesgloseMonedaFormaPagoPorSucursal",
            "obtenerDesgloseMonedaFormaPagoPorFecha",
            "obtenerDesgloseMonedaFormaPagoPorFechaYSucursal"})
    void elDesgloseQuedaEnLaMonedaOriginal(String metodo) {
        String sql = sqlDe(metodo);
        assertTrue(sql.contains("WHEN cd2.pago = true THEN cd2.valor WHEN cd2.vuelto = true THEN -ABS(cd2.valor)"),
                "el desglose se muestra con el simbolo de la moneda: no se convierte");
        assertFalse(sql.contains("SUM(cd.valor)"), "sumar el valor con signo suma los vueltos viejos en positivo");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "obtenerEstadisticasFormaPago",
            "obtenerEstadisticasFormaPagoPorSucursal",
            "obtenerEstadisticasFormaPagoPorFecha",
            "obtenerEstadisticasFormaPagoPorFechaYSucursal",
            "obtenerDesgloseMonedaFormaPago",
            "obtenerDesgloseMonedaFormaPagoPorSucursal",
            "obtenerDesgloseMonedaFormaPagoPorFecha",
            "obtenerDesgloseMonedaFormaPagoPorFechaYSucursal"})
    void laCantidadCuentaVentasPorSucursalYNoCuentaLasVacias(String metodo) {
        String sql = sqlDe(metodo);
        assertTrue(sql.contains(
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL)"),
                "la PK de venta es (id, sucursal_id), y con el LEFT JOIN (NULL, NULL) cuenta como 1");
        assertFalse(sql.contains("COUNT(DISTINCT cd.venta_id)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "obtenerEstadisticasFormaPago",
            "obtenerEstadisticasFormaPagoPorSucursal",
            "obtenerEstadisticasFormaPagoPorFecha",
            "obtenerEstadisticasFormaPagoPorFechaYSucursal",
            "obtenerDesgloseMonedaFormaPago",
            "obtenerDesgloseMonedaFormaPagoPorSucursal",
            "obtenerDesgloseMonedaFormaPagoPorFecha",
            "obtenerDesgloseMonedaFormaPagoPorFechaYSucursal"})
    void excluyeLosCobrosAtipicosComoIngresosVsGastos(String metodo) {
        String sql = sqlDe(metodo);
        assertTrue(sql.contains("ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000"),
                (esTotal(metodo) ? "el total" : "el desglose") + " no cierra con Ingresos vs Gastos");
    }
}
