package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.MovimientoBancario;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de los ajustes con saldo esperado contra la DB dev real (issue #376). Prueba lo que los mocks no
 * ven: que ajustes iguales y simultáneos entran una sola vez, que el saldo se compara contra la base
 * aunque la entidad ya esté cargada con el viejo, y que el reintento de un ajuste bancario no se repite
 * ni cuando el saldo volvió al esperado.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita commits
 * reales. Cada prueba deja el saldo como lo encontró.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=AjusteDeSaldoIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class AjusteDeSaldoIT {

    @Autowired private AjusteDeSaldoService service;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long cajaId;
    private Long monedaId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // Una caja de prueba (inactiva) si hay, y la primera moneda.
        List<?> cajas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.caja_virtual order by coalesce(activo, true), id").setMaxResults(1).getResultList());
        List<?> monedas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.moneda order by id").setMaxResults(1).getResultList());
        assumeTrue(!cajas.isEmpty() && !monedas.isEmpty(), "hacen falta una caja y una moneda");
        cajaId = ((Number) cajas.get(0)).longValue();
        monedaId = ((Number) monedas.get(0)).longValue();
    }

    private BigDecimal saldoCaja() {
        return tx.execute(s -> {
            List<?> f = em.createNativeQuery(
                    "select saldo from financiero.caja_virtual_saldo where caja_virtual_id = :c and moneda_id = :m")
                    .setParameter("c", cajaId).setParameter("m", monedaId).getResultList();
            return f.isEmpty() ? BigDecimal.ZERO : (BigDecimal) f.get(0);
        });
    }

    private long ajustesPorConteo() {
        return tx.execute(s -> ((Number) em.createNativeQuery(
                "select count(*) from financiero.movimiento_caja_virtual where caja_virtual_id = :c and moneda_id = :m "
                        + "and descripcion like 'AJUSTE POR CONTEO DE CAJA%'")
                .setParameter("c", cajaId).setParameter("m", monedaId).getSingleResult()).longValue());
    }

    @Test
    void seisAjustesPorConteoIgualesALaVezEntranUnaSolaVez() throws Exception {
        BigDecimal inicial = saldoCaja();
        double visto = inicial.doubleValue();
        double contado = visto + 7;
        long antes = ajustesPorConteo();
        try {
            ExecutorService pool = Executors.newFixedThreadPool(6);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Object>> futuros = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await(20, TimeUnit.SECONDS);
                    return service.ajustarCajaPorConteo(cajaId, monedaId, visto, contado, null);
                }));
            }
            largada.countDown();
            int exitos = 0;
            List<String> rechazos = new ArrayList<>();
            for (Future<Object> f : futuros) {
                try {
                    f.get(40, TimeUnit.SECONDS);
                    exitos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertTrue(e.getCause() instanceof GraphQLException, "error inesperado: " + e.getCause());
                    rechazos.add(e.getCause().getMessage());
                }
            }
            pool.shutdownNow();

            assertEquals(1, exitos, rechazos.toString());
            for (String r : rechazos) assertTrue(r.contains("ya coincide con lo contado"), r);
            assertEquals(0, inicial.add(BigDecimal.valueOf(7)).compareTo(saldoCaja()));
            assertEquals(antes + 1, ajustesPorConteo());
        } finally {
            // Otro conteo, de vuelta al saldo inicial.
            BigDecimal ahora = saldoCaja();
            if (ahora.compareTo(inicial) != 0) {
                service.ajustarCajaPorConteo(cajaId, monedaId, ahora.doubleValue(), visto, null);
            }
            assertEquals(0, inicial.compareTo(saldoCaja()), "la prueba dejó plata en la caja");
        }
    }

    @Test
    void elSaldoSeComparaContraLaBaseAunqueLaEntidadYaEsteCargadaConElViejo() {
        tx.execute(s -> {
            em.createNativeQuery("insert into financiero.caja_virtual_saldo (caja_virtual_id, moneda_id, saldo, creado_en) "
                    + "values (:c, :m, 0, now()) on conflict (caja_virtual_id, moneda_id) do nothing")
                    .setParameter("c", cajaId).setParameter("m", monedaId).executeUpdate();
            CajaVirtualSaldo cargado = em.createQuery("select x from CajaVirtualSaldo x where x.cajaVirtual.id = :c "
                    + "and x.moneda.id = :m", CajaVirtualSaldo.class)
                    .setParameter("c", cajaId).setParameter("m", monedaId).getSingleResult();
            double visto = cargado.getSaldo().doubleValue();
            // Lo que haría otra transacción ya commiteada: la fila cambia y la instancia cargada, no.
            em.createNativeQuery("update financiero.caja_virtual_saldo set saldo = saldo + 250 "
                    + "where caja_virtual_id = :c and moneda_id = :m")
                    .setParameter("c", cajaId).setParameter("m", monedaId).executeUpdate();
            assertEquals(visto, cargado.getSaldo().doubleValue());

            GraphQLException e = assertThrows(GraphQLException.class,
                    () -> service.ajustarCajaPorConteo(cajaId, monedaId, visto, visto + 9999, null));
            assertTrue(e.getMessage().contains("cambió desde que abriste el conteo"), e.getMessage());
            s.setRollbackOnly();
            return null;
        });
    }

    @Test
    void elReintentoDeUnAjusteBancarioNoSeRepiteAunqueElSaldoHayaVueltoAlEsperado() {
        List<?> cuentas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.cuenta_bancaria order by id").setMaxResults(1).getResultList());
        assumeTrue(!cuentas.isEmpty(), "la base no tiene ninguna cuenta bancaria");
        Long cuentaId = ((Number) cuentas.get(0)).longValue();
        BigDecimal inicial = saldoCuenta(cuentaId);
        double visto = inicial.doubleValue();
        String clave = "it-ajuste-" + System.nanoTime();

        MovimientoBancario original = service.ajustarSaldoBancario(cuentaId, 1.0, true, "IT AJUSTE", visto, clave, null);
        assertEquals(0, inicial.add(BigDecimal.ONE).compareTo(saldoCuenta(cuentaId)));

        // Sin clave, el saldo esperado viejo ya no pasa.
        GraphQLException viejo = assertThrows(GraphQLException.class,
                () -> service.ajustarSaldoBancario(cuentaId, 1.0, true, "IT AJUSTE", visto, null, null));
        assertTrue(viejo.getMessage().contains("cambió"), viejo.getMessage());

        // Entra un movimiento opuesto por el mismo monto: el saldo vuelve al que el cliente había visto.
        service.ajustarSaldoBancario(cuentaId, 1.0, false, "IT AJUSTE VUELTA", null, null, null);
        assertEquals(0, inicial.compareTo(saldoCuenta(cuentaId)));

        // El reintento trae el saldo esperado «correcto»: sin la clave se aplicaría otra vez.
        MovimientoBancario repetido = service.ajustarSaldoBancario(cuentaId, 1.0, true, "IT AJUSTE", visto, clave, null);
        assertEquals(original.getId(), repetido.getId());
        assertEquals(0, inicial.compareTo(saldoCuenta(cuentaId)), "el reintento volvió a ajustar");
    }

    private BigDecimal saldoCuenta(Long cuentaId) {
        return tx.execute(s -> (BigDecimal) em.createNativeQuery(
                "select saldo from financiero.cuenta_bancaria where id = :id")
                .setParameter("id", cuentaId).getSingleResult());
    }
}
