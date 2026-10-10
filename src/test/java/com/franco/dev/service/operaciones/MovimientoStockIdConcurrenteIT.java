package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * El choque del issue #153 contra una base real: movimientos de stock guardados a la vez en la
 * misma sucursal. Con MAX(id) + 1 varios calculaban el mismo id y solo uno entraba; con la
 * secuencia tienen que entrar todos, con ids distintos e impares.
 *
 * NO corre en CI (no hay base): se activa con -Dit.movimientoStock=true. Escribe de verdad —no
 * puede ser @Transactional, necesita commits concurrentes— pero con cantidad 0, inactivos y una
 * referencia que ningun dato real usa, y borra lo suyo al terminar.
 *
 * Va con el perfil dev y no es un detalle: sin perfil, application.properties deja prendidos los
 * schedulers de replicacion, que se conectan a las filiales reales que figuren en la base.
 *
 * Correr:  ./mvnw -DskipFlyway=true -Dit.movimientoStock=true -Dtest=MovimientoStockIdConcurrenteIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@EnabledIfSystemProperty(named = "it.movimientoStock", matches = "true")
class MovimientoStockIdConcurrenteIT {

    /** Ninguna entrada real tiene referencia negativa: todo lo que haya ahi lo creo esta prueba. */
    private static final long REFERENCIA = -153153L;
    private static final int HILOS = Integer.getInteger("it.movimientoStock.hilos", 8);
    private static final int MOVIMIENTOS = Integer.getInteger("it.movimientoStock.cantidad", 64);

    @Autowired private MovimientoStockService service;
    @Autowired private MovimientoStockLoteService loteService;
    @Autowired private JdbcTemplate jdbc;

    private boolean limpiar;

    @BeforeEach
    void setUp() {
        assumeTrue(contarMovimientos() == 0,
                "ya hay movimientos con referencia " + REFERENCIA + " (¿una corrida anterior que se corto?):"
                        + " no se toca nada que esta prueba no haya creado. Revisar y borrar a mano.");
        limpiar = true;
    }

    @AfterEach
    void limpiar() {
        if (limpiar) {
            jdbc.update("delete from operaciones.movimiento_stock where referencia = ? and tipo_movimiento = 'ENTRADA'",
                    REFERENCIA);
        }
    }

    private int contarMovimientos() {
        return jdbc.queryForObject(
                "select count(*) from operaciones.movimiento_stock where referencia = ? and tipo_movimiento = 'ENTRADA'",
                Integer.class, REFERENCIA);
    }

    private MovimientoStock guardar(Long sucursalId, Long productoId) {
        Producto producto = new Producto();
        producto.setId(productoId);
        MovimientoStock ms = new MovimientoStock();
        ms.setSucursalId(sucursalId);
        ms.setProducto(producto);
        ms.setTipoMovimiento(TipoMovimiento.ENTRADA);
        ms.setReferencia(REFERENCIA);
        ms.setCantidad(0.0);
        ms.setEstado(false);
        return service.save(ms);
    }

    /**
     * Larga {@code cantidad} guardados en tandas de HILOS, todos juntos dentro de cada tanda —es lo
     * que provocaba el choque—, y exige que ninguno falle ni repita id, y que todos sean impares.
     */
    private void guardarALaVez(int cantidad, IntFunction<Long> guardarYDevolverId) throws Exception {
        Queue<Long> ids = new ConcurrentLinkedQueue<>();
        Queue<Throwable> fallas = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        for (int desde = 0; desde < cantidad; desde += HILOS) {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Void>> enCurso = new ArrayList<>();
            for (int i = desde; i < Math.min(desde + HILOS, cantidad); i++) {
                int n = i;
                enCurso.add(pool.submit((Callable<Void>) () -> {
                    largada.await();
                    try {
                        ids.add(guardarYDevolverId.apply(n));
                    } catch (Throwable t) {
                        fallas.add(t);
                    }
                    return null;
                }));
            }
            largada.countDown();
            for (Future<Void> f : enCurso) {
                f.get(60, TimeUnit.SECONDS);
            }
        }
        pool.shutdown();

        assertTrue(fallas.isEmpty(), fallas.size() + " guardados fallaron; el primero: " + fallas.peek());
        assertEquals(cantidad, new HashSet<>(ids).size(), "hay ids repetidos");
        assertTrue(ids.stream().allMatch(id -> id % 2 == 1), "el central genero un id par: " + ids);
    }

    private Long unProducto() {
        List<Long> productos = jdbc.queryForList("select id from productos.producto order by id limit 1", Long.class);
        assumeTrue(productos.size() == 1, "falta un producto de referencia");
        return productos.get(0);
    }

    private List<Long> sucursales(int cuantas) {
        List<Long> sucursales = jdbc.queryForList(
                "select id from empresarial.sucursal order by id limit ?", Long.class, cuantas);
        assumeTrue(sucursales.size() == cuantas, "faltan sucursales de referencia");
        return sucursales;
    }

    @Test
    void movimientosSimultaneosDeUnaSucursalNoRepitenId() throws Exception {
        Long sucursalId = sucursales(1).get(0);
        Long productoId = unProducto();

        guardarALaVez(MOVIMIENTOS, n -> guardar(sucursalId, productoId).getId());

        assertEquals(MOVIMIENTOS, contarMovimientos(), "no todos los movimientos quedaron en la base");
    }

    /**
     * La secuencia es una sola para todas las sucursales: guardados simultaneos en sucursales
     * distintas tampoco se estorban, y el id no se repite ni siquiera entre sucursales.
     */
    @Test
    void movimientosSimultaneosDeVariasSucursalesNoRepitenId() throws Exception {
        List<Long> sucursales = sucursales(4);
        Long productoId = unProducto();

        guardarALaVez(MOVIMIENTOS, n -> guardar(sucursales.get(n % sucursales.size()), productoId).getId());

        assertEquals(MOVIMIENTOS, contarMovimientos(), "no todos los movimientos quedaron en la base");
    }

    /** El desglose por lote tenia el mismo MAX(id) + 1: varias filas del mismo movimiento a la vez. */
    @Test
    void filasDeLoteSimultaneasNoRepitenId() throws Exception {
        Long sucursalId = sucursales(1).get(0);
        Long productoId = unProducto();
        // Las filas de lote se van con su movimiento (ON DELETE CASCADE) cuando limpiar() lo borra.
        Long movimientoId = guardar(sucursalId, productoId).getId();

        guardarALaVez(MOVIMIENTOS, n -> {
            Producto producto = new Producto();
            producto.setId(productoId);
            MovimientoStockLote fila = new MovimientoStockLote();
            fila.setSucursalId(sucursalId);
            fila.setMovimientoStockId(movimientoId);
            fila.setProducto(producto);
            fila.setNumeroLote("IT-153-" + n);
            fila.setCantidad(0.0);
            fila.setEstado(false);
            fila.setReferencia(REFERENCIA);
            return loteService.save(fila).getId();
        });

        assertEquals(MOVIMIENTOS, jdbc.queryForObject(
                "select count(*) from operaciones.movimiento_stock_lote where movimiento_stock_id = ? and sucursal_id = ?",
                Integer.class, movimientoId, sucursalId), "no todas las filas de lote quedaron en la base");
    }
}
