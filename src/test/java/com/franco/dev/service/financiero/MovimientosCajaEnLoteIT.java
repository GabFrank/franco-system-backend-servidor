package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.MovimientosCajaEnLoteService.Monto;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de los movimientos de caja en varias monedas contra la DB dev real (issue #376). Prueba lo que los
 * mocks no ven: que el lote entra entero o no entra, que la clave de idempotencia evita el segundo
 * registro, y que lotes cruzados y movimientos sueltos a la vez no se traban.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita commits
 * reales. Todo lo que registra lleva una marca en la descripción y se anula al terminar (saldo neto cero).
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=MovimientosCajaEnLoteIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class MovimientosCajaEnLoteIT {

    @Autowired private MovimientosCajaEnLoteService loteService;
    @Autowired private TesoreriaService tesoreriaService;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Usuario usuario;
    private Long cajaA;
    private Long cajaB;
    private Long gs;
    private Long rs;
    private String marca;
    private String saldosIniciales;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // Dos cajas, primero las inactivas (las de prueba), y dos monedas.
        List<?> cajas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.caja_virtual order by coalesce(activo, true), id").setMaxResults(2).getResultList());
        List<?> monedas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.moneda order by id").setMaxResults(2).getResultList());
        assumeTrue(cajas.size() == 2 && monedas.size() == 2, "hacen falta dos cajas y dos monedas");
        cajaA = ((Number) cajas.get(0)).longValue();
        cajaB = ((Number) cajas.get(1)).longValue();
        gs = ((Number) monedas.get(0)).longValue();
        rs = ((Number) monedas.get(1)).longValue();
        usuario = tx.execute(s -> em.createQuery("select u from Usuario u order by u.id", Usuario.class)
                .setMaxResults(1).getSingleResult());
        marca = "IT LOTE " + System.nanoTime();
        saldosIniciales = saldos();
    }

    /** Anula todo lo que registró la prueba y comprueba que las cajas quedaron como estaban. */
    @AfterEach
    void limpiar() {
        if (marca == null) return;
        while (true) {
            List<?> activos = tx.execute(s -> em.createNativeQuery(
                    "select id from financiero.movimiento_caja_virtual where descripcion = :d and activo order by id desc")
                    .setParameter("d", marca).setMaxResults(1).getResultList());
            if (activos.isEmpty()) break;
            tesoreriaService.anular(((Number) activos.get(0)).longValue(), "IT LOTE", null);
        }
        assertEquals(saldosIniciales, saldos(), "la prueba dejó plata en las cajas");
    }

    private String saldos() {
        return tx.execute(s -> String.valueOf(em.createNativeQuery(
                "select coalesce(string_agg(caja_virtual_id || ':' || moneda_id || '=' || cast(saldo as text), ' ' "
                        + "order by caja_virtual_id, moneda_id), '') from financiero.caja_virtual_saldo "
                        + "where caja_virtual_id in (:a, :b) and moneda_id in (:g, :r) and saldo <> 0")
                .setParameter("a", cajaA).setParameter("b", cajaB).setParameter("g", gs).setParameter("r", rs)
                .getSingleResult()));
    }

    private long movimientos() {
        return tx.execute(s -> ((Number) em.createNativeQuery(
                "select count(*) from financiero.movimiento_caja_virtual where descripcion = :d")
                .setParameter("d", marca).getSingleResult()).longValue());
    }

    private static List<Monto> montos(Object... pares) {
        List<Monto> lista = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            lista.add(new Monto((Long) pares[i], ((Number) pares[i + 1]).doubleValue()));
        }
        return lista;
    }

    private void ingreso(Long caja, Object... pares) {
        loteService.registrarMovimientos(caja, CajaVirtualTipoMovimiento.INGRESO, montos(pares), marca, usuario, null);
    }

    @Test
    void siUnaMonedaNoTieneSaldoNoEntraNinguna() {
        ingreso(cajaA, gs, 1000);
        String antes = saldos();
        long movimientosAntes = movimientos();

        // Hay guaraníes para el egreso, no hay reales: antes entraba la primera y quedaba a medias.
        GraphQLException e = assertThrows(GraphQLException.class, () -> loteService.registrarMovimientos(cajaA,
                CajaVirtualTipoMovimiento.EGRESO, montos(gs, 100, rs, 50), marca, usuario, null));

        assertTrue(e.getMessage().contains("Saldo insuficiente"), e.getMessage());
        assertEquals(antes, saldos());
        assertEquals(movimientosAntes, movimientos());
    }

    @Test
    void unaTransferenciaEnDosMonedasSinSaldoEnUnaNoTransfiereNinguna() {
        ingreso(cajaA, gs, 1000);
        String antes = saldos();
        long movimientosAntes = movimientos();

        assertThrows(GraphQLException.class, () -> loteService.transferir(cajaA, cajaB,
                montos(gs, 100, rs, 50), marca, usuario, null));

        assertEquals(antes, saldos());
        assertEquals(movimientosAntes, movimientos());
    }

    @Test
    void elMismoPedidoConLaMismaClaveSeRegistraUnaSolaVezYAnuladoSeRechaza() {
        String clave = "it-lote-" + System.nanoTime();
        List<Monto> pedido = montos(gs, 700, rs, 30);

        assertTrue(loteService.registrarMovimientos(cajaA, CajaVirtualTipoMovimiento.INGRESO, pedido, marca, usuario, clave));
        String despuesDelPrimero = saldos();
        assertTrue(loteService.registrarMovimientos(cajaA, CajaVirtualTipoMovimiento.INGRESO, pedido, marca, usuario, clave));

        assertEquals(2, movimientos());
        assertEquals(despuesDelPrimero, saldos());

        // La misma clave no vale para otro contenido.
        GraphQLException otro = assertThrows(GraphQLException.class, () -> loteService.registrarMovimientos(cajaA,
                CajaVirtualTipoMovimiento.INGRESO, montos(gs, 701, rs, 30), marca, usuario, clave));
        assertTrue(otro.getMessage().contains("otro pedido"), otro.getMessage());

        // Anulado el primer movimiento, el repetido no responde «registrado» ni registra de nuevo.
        Long primero = tx.execute(s -> ((Number) em.createNativeQuery(
                "select min(id) from financiero.movimiento_caja_virtual where descripcion = :d")
                .setParameter("d", marca).getSingleResult()).longValue());
        tesoreriaService.anular(primero, "IT LOTE", null);
        GraphQLException anulado = assertThrows(GraphQLException.class, () -> loteService.registrarMovimientos(cajaA,
                CajaVirtualTipoMovimiento.INGRESO, pedido, marca, usuario, clave));
        assertTrue(anulado.getMessage().contains("después fue anulado"), anulado.getMessage());
    }

    @Test
    void unaTransferenciaEnDosMonedasDejaCadaParDePatasVinculado() {
        ingreso(cajaA, gs, 1000, rs, 100);

        assertTrue(loteService.transferir(cajaA, cajaB, montos(rs, 40, gs, 300), marca, usuario, null));

        List<?> sueltas = tx.execute(s -> em.createNativeQuery(
                "select m.id from financiero.movimiento_caja_virtual m where m.descripcion = :d "
                        + "and cast(m.tipo_movimiento as text) like 'TRANSFERENCIA%' and not exists ("
                        + "select 1 from financiero.movimiento_caja_virtual o where o.id = m.referencia_id "
                        + "and o.referencia_id = m.id and o.moneda_id = m.moneda_id and o.cantidad = m.cantidad)")
                .setParameter("d", marca).getResultList());
        assertTrue(sueltas.isEmpty(), "patas sin su par: " + sueltas);
        assertEquals(2 + 4, movimientos());
    }

    @Test
    void lotesCruzadosYMovimientosSueltosALaVezNoSeTraban() throws Exception {
        ingreso(cajaA, gs, 100000, rs, 10000);
        ingreso(cajaB, gs, 100000, rs, 10000);

        for (int ronda = 0; ronda < 5; ronda++) {
            List<Callable<Object>> acciones = Arrays.asList(
                    () -> loteService.transferir(cajaA, cajaB, montos(gs, 10, rs, 1), marca, usuario, null),
                    () -> loteService.transferir(cajaB, cajaA, montos(rs, 1, gs, 10), marca, usuario, null),
                    // Un movimiento suelto en la segunda moneda: el que se cruzaba con el shim de la caja.
                    () -> loteService.registrarMovimientos(cajaA, CajaVirtualTipoMovimiento.EGRESO, montos(rs, 1), marca, usuario, null),
                    () -> loteService.registrarMovimientos(cajaB, CajaVirtualTipoMovimiento.EGRESO, montos(rs, 1), marca, usuario, null),
                    () -> loteService.registrarMovimientos(cajaA, CajaVirtualTipoMovimiento.INGRESO, montos(gs, 5, rs, 2), marca, usuario, null),
                    () -> loteService.registrarMovimientos(cajaB, CajaVirtualTipoMovimiento.AJUSTE, montos(gs, -5, rs, 2), marca, usuario, null));

            List<Throwable> errores = aLaVez(acciones);
            assertTrue(errores.isEmpty(), "ronda " + ronda + ": " + errores);
        }
    }

    private List<Throwable> aLaVez(List<Callable<Object>> acciones) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(acciones.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<Object> accion : acciones) {
                futuros.add(pool.submit(() -> {
                    largada.await(20, TimeUnit.SECONDS);
                    return accion.call();
                }));
            }
            largada.countDown();
            List<Throwable> errores = new ArrayList<>();
            for (Future<Object> f : futuros) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    errores.add(e.getCause());
                }
            }
            return errores;
        } finally {
            pool.shutdownNow();
        }
    }

}
