package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.Retiro;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
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
 * IT de cancelar / habilitar un retiro y un gasto contra la DB dev real (issue #376). Prueba lo que los
 * mocks no ven: que los UPDATE dirigidos y las proyecciones funcionan contra el enum de PostgreSQL, que
 * pedidos repetidos y simultáneos no invierten el estado, y que una cancelación y un ingreso a caja
 * mayor a la vez no dejan el retiro cancelado con la plata acreditada.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: la concurrencia
 * necesita commits reales. Usa un retiro y un gasto existentes y los deja como estaban.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=CancelarRetiroIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class CancelarRetiroIT {

    private static final int RONDAS = 5;

    @Autowired private RetiroService retiroService;
    @Autowired private GastoService gastoService;
    @Autowired private RetiroIngresoService retiroIngresoService;
    @Autowired private TesoreriaService tesoreriaService;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long retiroId;
    private Long sucursalId;
    private Long cajaMayorId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // Un retiro flotante: sin estado (como casi todos), sin caja mayor y sin verificación, con plata.
        List<Object[]> filas = filas("select r.id, r.sucursal_id from financiero.retiro r "
                + "where r.estado is null and r.caja_virtual_id is null and r.movimiento_caja_virtual_id is null "
                + "and not exists (select 1 from financiero.retiro_verificacion v where v.retiro_id = r.id and v.sucursal_id = r.sucursal_id) "
                + "and exists (select 1 from financiero.retiro_detalle d where d.retiro_id = r.id and d.sucursal_id = r.sucursal_id and d.cantidad > 0) "
                + "order by r.creado_en desc limit 1");
        assumeTrue(!filas.isEmpty(), "la base no tiene ningún retiro flotante");
        retiroId = ((Number) filas.get(0)[0]).longValue();
        sucursalId = ((Number) filas.get(0)[1]).longValue();
        List<?> cajas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.caja_virtual order by id").setMaxResults(1).getResultList());
        assumeTrue(cajas != null && !cajas.isEmpty(), "la base no tiene ninguna caja mayor");
        cajaMayorId = ((Number) cajas.get(0)).longValue();
    }

    @AfterEach
    void dejarComoEstaba() {
        if (retiroId == null) return;
        tx.execute(s -> em.createNativeQuery("update financiero.retiro set estado = null, caja_virtual_id = null, "
                + "movimiento_caja_virtual_id = null where id = :id and sucursal_id = :s")
                .setParameter("id", retiroId).setParameter("s", sucursalId).executeUpdate());
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> filas(String sql) {
        return tx.execute(s -> (List<Object[]>) em.createNativeQuery(sql).getResultList());
    }

    private String estado() {
        return tx.execute(s -> (String) em.createNativeQuery(
                "select cast(estado as text) from financiero.retiro where id = :id and sucursal_id = :s")
                .setParameter("id", retiroId).setParameter("s", sucursalId).getSingleResult());
    }

    private Long movimientoDelRetiro() {
        return tx.execute(s -> {
            Object m = em.createNativeQuery(
                    "select movimiento_caja_virtual_id from financiero.retiro where id = :id and sucursal_id = :s")
                    .setParameter("id", retiroId).setParameter("s", sucursalId).getSingleResult();
            return m != null ? ((Number) m).longValue() : null;
        });
    }

    /** Lanza todas las acciones a la vez. Devuelve los mensajes de las que se rechazaron. */
    private List<String> aLaVez(List<Callable<Object>> acciones) throws Exception {
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
            List<String> rechazos = new ArrayList<>();
            for (Future<Object> f : futuros) {
                try {
                    f.get(40, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    assertTrue(e.getCause() instanceof GraphQLException, "error inesperado: " + e.getCause());
                    rechazos.add(e.getCause().getMessage());
                }
            }
            return rechazos;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancelarYHabilitarRepetidosYSimultaneosNoInviertenElEstado() throws Exception {
        List<Callable<Object>> cancelar = new ArrayList<>();
        List<Callable<Object>> habilitar = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            cancelar.add(() -> retiroService.cancelarRetiro(retiroId, sucursalId, true));
            habilitar.add(() -> retiroService.cancelarRetiro(retiroId, sucursalId, false));
        }

        assertTrue(aLaVez(cancelar).isEmpty());
        assertEquals("CANCELADO", estado());

        assertTrue(aLaVez(habilitar).isEmpty());
        assertEquals("CONCLUIDO", estado());

        // Sin argumento (desktop anterior): cancela, y repetido se rechaza en vez de habilitar.
        assertTrue(retiroService.cancelarRetiro(retiroId, sucursalId, null));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> retiroService.cancelarRetiro(retiroId, sucursalId, null));
        assertTrue(e.getMessage().contains("ya está cancelado"), e.getMessage());
        assertEquals("CANCELADO", estado());
    }

    @Test
    void unRetiroCanceladoNoApareceEntreLosFlotantesYLosDeEstadoNuloSi() {
        long antes = retiroService.findFlotantes(sucursalId, null, null, null, PageRequest.of(0, 1)).getTotalElements();
        assertTrue(antes > 0, "el retiro de estado nulo tiene que estar entre los flotantes");

        retiroService.cancelarRetiro(retiroId, sucursalId, true);

        long despues = retiroService.findFlotantes(sucursalId, null, null, null, PageRequest.of(0, 1)).getTotalElements();
        assertEquals(antes - 1, despues);
    }

    @Test
    void cancelarEIngresarALaCajaMayorALaVezNuncaDejanUnCanceladoConLaPlataAcreditada() throws Exception {
        for (int ronda = 0; ronda < RONDAS; ronda++) {
            List<Callable<Object>> acciones = new ArrayList<>();
            acciones.add(() -> retiroService.cancelarRetiro(retiroId, sucursalId, true));
            acciones.add(() -> retiroIngresoService.ingresarACajaMayor(retiroId, sucursalId, cajaMayorId, null));

            List<String> rechazos = aLaVez(acciones);

            assertEquals(1, rechazos.size(), "tiene que ganar uno solo, ronda " + ronda + ": " + rechazos);
            Long movimiento = movimientoDelRetiro();
            if ("CANCELADO".equals(estado())) {
                assertNull(movimiento, "cancelado y con ingreso en la caja mayor");
                assertTrue(rechazos.get(0).contains("está cancelado"), rechazos.get(0));
            } else {
                assertNotNull(movimiento, "no quedó ni cancelado ni ingresado");
                assertTrue(rechazos.get(0).contains("ya entró a la caja mayor"), rechazos.get(0));
                // Devuelve lo acreditado: la prueba no deja plata en la caja.
                tx.execute(s -> {
                    List<MovimientoCajaVirtual> movs = em.createQuery("select m from MovimientoCajaVirtual m "
                            + "where m.origenTipo = com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo.RETIRO_CAJA "
                            + "and m.origenId = :id and m.activo = true and m.descripcion like :d", MovimientoCajaVirtual.class)
                            .setParameter("id", retiroId).setParameter("d", "Retiro #" + retiroId + " - %").getResultList();
                    for (MovimientoCajaVirtual m : movs) tesoreriaService.revertir(m, "IT CANCELAR RETIRO", null);
                    return null;
                });
            }
            dejarComoEstaba();
        }
    }

    @Test
    void cancelarYHabilitarUnGastoRepetidosNoInviertenElEstado() {
        List<Object[]> filas = filas("select g.id, g.sucursal_id from financiero.gasto g "
                + "where g.cancelado is null and g.solicitud_pago_id is null order by g.creado_en desc limit 1");
        assumeTrue(!filas.isEmpty(), "la base no tiene ningún gasto sin cancelar");
        Long gastoId = ((Number) filas.get(0)[0]).longValue();
        Long gastoSuc = ((Number) filas.get(0)[1]).longValue();
        try {
            assertTrue(gastoService.cancelarGasto(gastoId, gastoSuc, true));
            assertTrue(gastoService.cancelarGasto(gastoId, gastoSuc, true));
            assertEquals(Boolean.TRUE, canceladoDe(gastoId, gastoSuc));

            assertTrue(gastoService.cancelarGasto(gastoId, gastoSuc, false));
            assertTrue(gastoService.cancelarGasto(gastoId, gastoSuc, false));
            assertEquals(Boolean.FALSE, canceladoDe(gastoId, gastoSuc));
        } finally {
            tx.execute(s -> em.createNativeQuery("update financiero.gasto set cancelado = null where id = :id and sucursal_id = :s")
                    .setParameter("id", gastoId).setParameter("s", gastoSuc).executeUpdate());
        }
    }

    private Boolean canceladoDe(Long id, Long suc) {
        return tx.execute(s -> (Boolean) em.createNativeQuery(
                "select cancelado from financiero.gasto where id = :id and sucursal_id = :s")
                .setParameter("id", id).setParameter("s", suc).getSingleResult());
    }
}
