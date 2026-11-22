package com.franco.dev.repository.operaciones;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tanda de ventas, reportes y notificaciones de #674: rankings con tope, busqueda de productos,
 * movimientos por lote y notificaciones terminan su orden en una clave unica. Corre en CI.
 */
class OrdenTotalVentasTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "operaciones.VentaRepository                 | getVentasPorFuncionario           | ORDER BY SUM(v.total_gs) DESC, u.id ASC",
            "operaciones.VentaRepository                 | getVentasPorFuncionarioBySucursal | ORDER BY SUM(v.total_gs) DESC, u.id ASC",
            "operaciones.VentaRepository                 | getVentasPorFuncionarioByUsuario  | ORDER BY SUM(v.total_gs) DESC, u.id ASC",
            "operaciones.VentaRepository                 | getVentasPorFuncionarioAll        | ORDER BY SUM(v.total_gs) DESC, u.id ASC",
            "operaciones.VentaItemRepository             | findTopProductoByUsuario          | ORDER BY SUM(vi.cantidad) DESC, p.id ASC",
            "operaciones.MovimientoStockLoteRepository   | movimientosPorLote                | msl.id DESC, msl.sucursal_id DESC",
            "operaciones.MovimientoStockLoteRepository   | clientesPorLote                   | v.id DESC, v.sucursal_id DESC",
            "productos.ProductoRepository                | findbyAll                         | ORDER BY p.descripcion asc, p.id asc limit 10 offset :offset",
            "productos.ProductoRepository                | findEnvases                       | ORDER BY p.descripcion asc, p.id asc limit 10 offset ?2",
            "configuracion.NotificacionDestinatarioRepository | findByUsuarioId              | ORDER BY n.creadoEn DESC, nd.id DESC",
            "configuracion.NotificacionDestinatarioRepository | findByUsuarioIdAndFilters    | ORDER BY n.creadoEn DESC, nd.id DESC"
    })
    void elOrdenTerminaEnUnaClaveUnica(String repo, String metodo, String final_) throws Exception {
        List<String> consultas = Arrays.stream(Class.forName("com.franco.dev.repository." + repo).getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertFalse(consultas.isEmpty(), metodo);
        consultas.forEach(q -> assertTrue(q.endsWith(final_), metodo + ": " + q));
    }

    @Test
    void lotesPendientesDesempatanPorSucursal() throws Exception {
        String fuente = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/franco/dev/service/operaciones/LotePendienteService.java")));
        assertTrue(fuente.contains("ORDER BY msl.creado_en DESC, msl.id DESC, msl.sucursal_id DESC LIMIT"));
    }

    @Test
    void notificacionesDelUsuarioDesempatanPorId() throws Exception {
        String fuente = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/franco/dev/graphql/configuracion/NotificacionUsuarioGraphQL.java")));
        assertTrue(fuente.contains("Sort.by(Sort.Direction.DESC, \"notificacion.creadoEn\", \"id\")"));
    }
}
