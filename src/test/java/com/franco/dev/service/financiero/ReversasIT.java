package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.EntradaVaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de las reversas contra la DB dev real (issue #376): un movimiento se revierte una sola vez. Es la
 * única prueba automática de lo que los tests con mocks no pueden ver: que el estado se lee de la base
 * aunque la entidad ya esté cargada, y que dos reversas simultáneas se serializan en el lock.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true.
 * No es @Transactional: las pruebas de concurrencia necesitan commits reales. Lo que commitean son
 * ingresos y transferencias de 1 con su reversa (saldo neto cero), con descripción "IT REVERSAS".
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=ReversasIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class ReversasIT {

    private static final String MARCA = "IT REVERSAS";
    private static final int RONDAS = 5;

    @Autowired private TesoreriaService tesoreriaService;
    @Autowired private MovimientoCajaVirtualService movimientoCajaVirtualService;
    @Autowired private EntradaVariaService entradaVariaService;
    @Autowired private BancoLedgerService bancoLedgerService;
    @Autowired private MovimientoBancarioRepository movimientoBancarioRepository;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long cajaId;
    private Long monedaId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        List<Object[]> filas = tx.execute(s -> em.createQuery(
                "select s.cajaVirtual.id, s.moneda.id from CajaVirtualSaldo s order by s.saldo desc", Object[].class)
                .setMaxResults(1).getResultList());
        assumeTrue(filas != null && !filas.isEmpty(), "la base no tiene ninguna caja con saldo");
        cajaId = (Long) filas.get(0)[0];
        monedaId = (Long) filas.get(0)[1];
    }

    private BigDecimal saldoCaja() {
        return tx.execute(s -> (BigDecimal) em.createNativeQuery(
                "select saldo from financiero.caja_virtual_saldo where caja_virtual_id = :c and moneda_id = :m")
                .setParameter("c", cajaId).setParameter("m", monedaId).getSingleResult());
    }

    private long contrasDe(Long movimientoId) {
        return tx.execute(s -> ((Number) em.createNativeQuery(
                "select count(*) from financiero.movimiento_caja_virtual where origen_tipo = 'ANULACION' and origen_id = :id")
                .setParameter("id", movimientoId).getSingleResult()).longValue());
    }

    private MovimientoCajaVirtual ingresoDeUno() {
        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setCajaVirtual(em.getReference(CajaVirtual.class, cajaId));
        m.setMoneda(em.getReference(Moneda.class, monedaId));
        m.setTipoMovimiento(CajaVirtualTipoMovimiento.INGRESO);
        m.setCantidad(1.0);
        m.setDescripcion(MARCA);
        m.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        m.setActivo(true);
        return m;
    }

    /** Lanza {@code n} veces la misma acción a la vez. Devuelve [éxitos, rechazos «ya está anulad…»]. */
    private int[] aLaVez(int n, Callable<Object> accion) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await(20, TimeUnit.SECONDS);
                    return accion.call();
                }));
            }
            largada.countDown();
            int exitos = 0, rechazos = 0;
            for (Future<Object> f : futuros) {
                try {
                    f.get(40, TimeUnit.SECONDS);
                    exitos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable causa = e.getCause();
                    assertTrue(causa instanceof GraphQLException && causa.getMessage().contains("ya está anulad"),
                            "rechazo inesperado: " + causa);
                    rechazos++;
                }
            }
            return new int[]{exitos, rechazos};
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void revertirLeeElEstadoDeLaBaseAunqueLaEntidadYaCargadaDigaActivo() {
        tx.execute(s -> {
            MovimientoCajaVirtual mov = tesoreriaService.registrar(ingresoDeUno());
            em.flush();
            // Lo que haría otra transacción ya commiteada: la fila queda inactiva y la instancia, no.
            em.createNativeQuery("update financiero.movimiento_caja_virtual set activo = false where id = :id")
                    .setParameter("id", mov.getId()).executeUpdate();
            assertNotEquals(Boolean.FALSE, mov.getActivo());

            GraphQLException e = assertThrows(GraphQLException.class,
                    () -> tesoreriaService.revertir(mov, MARCA, null));
            assertTrue(e.getMessage().contains("ya está anulado"), e.getMessage());
            s.setRollbackOnly();
            return null;
        });
    }

    @Test
    void dosReversasSimultaneasDelMismoMovimientoDejanUnSoloContraMovimiento() throws Exception {
        for (int ronda = 0; ronda < RONDAS; ronda++) {
            BigDecimal saldoInicial = saldoCaja();
            Long movId = tx.execute(s -> tesoreriaService.registrar(ingresoDeUno()).getId());

            // Dentro de una transacción, como lo llaman el vale y la liquidación desde su anular.
            int[] r = aLaVez(3, () -> tx.execute(
                    s -> movimientoCajaVirtualService.revertirMovimiento(movId, MARCA, null)));

            assertEquals(1, r[0], "éxitos en la ronda " + ronda);
            assertEquals(2, r[1], "rechazos en la ronda " + ronda);
            assertEquals(1, contrasDe(movId));
            assertEquals(0, saldoInicial.compareTo(saldoCaja()), "el saldo no volvió al inicial");
        }
    }

    @Test
    void dosAnulacionesSimultaneasDeUnaEntradaVariaDevuelvenLaPlataUnaSolaVez() throws Exception {
        for (int ronda = 0; ronda < RONDAS; ronda++) {
            BigDecimal saldoInicial = saldoCaja();
            EntradaVaria nueva = new EntradaVaria();
            nueva.setCajaVirtual(tx.execute(s -> em.find(CajaVirtual.class, cajaId)));
            nueva.setMoneda(tx.execute(s -> em.find(Moneda.class, monedaId)));
            nueva.setEsIngreso(true);
            nueva.setMonto(BigDecimal.ONE);
            nueva.setDescripcion(MARCA);
            nueva.setNumeroComprobante("IT-" + System.nanoTime());
            EntradaVaria creada = entradaVariaService.registrar(nueva, null);

            int[] r = aLaVez(3, () -> entradaVariaService.anular(creada.getId(), MARCA, null));

            assertEquals(1, r[0], "éxitos en la ronda " + ronda);
            assertEquals(2, r[1], "rechazos en la ronda " + ronda);
            assertEquals(1, contrasDe(creada.getMovimientoCajaVirtualId()));
            assertEquals(0, saldoInicial.compareTo(saldoCaja()), "el saldo no volvió al inicial");
        }
    }

    @Test
    void dosReversasSimultaneasDelMismoMovimientoBancarioDejanUnSoloAjuste() throws Exception {
        List<?> cuentas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.cuenta_bancaria order by id").setMaxResults(1).getResultList());
        assumeTrue(cuentas != null && !cuentas.isEmpty(), "la base no tiene ninguna cuenta bancaria");
        Long cuentaId = ((Number) cuentas.get(0)).longValue();

        for (int ronda = 0; ronda < RONDAS; ronda++) {
            BigDecimal saldoInicial = saldoCuenta(cuentaId);
            Long movId = bancoLedgerService.registrar(cuentaId, MovimientoBancarioTipo.ENTRADA_MANUAL,
                    BigDecimal.ONE, MARCA, "MANUAL", null, null).getId();

            int[] r = aLaVez(3, () -> tx.execute(s -> {
                MovimientoBancario mov = movimientoBancarioRepository.findById(movId).orElseThrow(IllegalStateException::new);
                return bancoLedgerService.revertir(mov, MARCA, null);
            }));

            assertEquals(1, r[0], "éxitos en la ronda " + ronda);
            assertEquals(2, r[1], "rechazos en la ronda " + ronda);
            long ajustes = tx.execute(s -> ((Number) em.createNativeQuery(
                    "select count(*) from financiero.movimiento_bancario where origen_tipo = 'ANULACION' and origen_id = :id")
                    .setParameter("id", movId).getSingleResult()).longValue());
            assertEquals(1, ajustes);
            assertEquals(0, saldoInicial.compareTo(saldoCuenta(cuentaId)), "el saldo no volvió al inicial");
        }
    }

    @Test
    void dosAnulacionesSimultaneasDeUnaTransferenciaCadaUnaPorUnaPataLaAnulanUnaSolaVezYCompleta() throws Exception {
        List<?> otras = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.caja_virtual where id <> :c order by id")
                .setParameter("c", cajaId).setMaxResults(1).getResultList());
        assumeTrue(otras != null && !otras.isEmpty(), "la base no tiene una segunda caja");
        Long destinoId = ((Number) otras.get(0)).longValue();

        for (int ronda = 0; ronda < RONDAS; ronda++) {
            BigDecimal origenInicial = saldoCaja();
            BigDecimal destinoInicial = saldoDe(destinoId);
            String marca = MARCA + " T" + System.nanoTime();
            tesoreriaService.transferir(cajaId, destinoId, 1.0, tx.execute(s -> em.find(Moneda.class, monedaId)), marca, null);
            List<?> patas = tx.execute(s -> em.createNativeQuery(
                    "select id, referencia_id from financiero.movimiento_caja_virtual where descripcion = :d order by id")
                    .setParameter("d", marca).getResultList());
            assertEquals(2, patas.size());
            Long unaId = ((Number) ((Object[]) patas.get(0))[0]).longValue();
            Long otraId = ((Number) ((Object[]) patas.get(1))[0]).longValue();
            // Las dos patas quedan apuntándose entre sí.
            assertEquals(otraId, ((Number) ((Object[]) patas.get(0))[1]).longValue());
            assertEquals(unaId, ((Number) ((Object[]) patas.get(1))[1]).longValue());

            // Cada hilo entra por una pata distinta: si cada uno tomara «la suya» primero, se cruzarían.
            java.util.concurrent.atomic.AtomicInteger turno = new java.util.concurrent.atomic.AtomicInteger();
            int[] r = aLaVez(4, () -> tesoreriaService.anular(
                    turno.getAndIncrement() % 2 == 0 ? unaId : otraId, MARCA, null));

            assertEquals(1, r[0], "éxitos en la ronda " + ronda);
            assertEquals(3, r[1], "rechazos en la ronda " + ronda);
            assertEquals(1, contrasDe(unaId));
            assertEquals(1, contrasDe(otraId));
            assertEquals(0, origenInicial.compareTo(saldoCaja()), "el saldo de la caja origen no volvió al inicial");
            assertEquals(0, destinoInicial.compareTo(saldoDe(destinoId)), "el saldo de la caja destino no volvió al inicial");
        }
    }

    private BigDecimal saldoDe(Long caja) {
        return tx.execute(s -> {
            List<?> f = em.createNativeQuery(
                    "select saldo from financiero.caja_virtual_saldo where caja_virtual_id = :c and moneda_id = :m")
                    .setParameter("c", caja).setParameter("m", monedaId).getResultList();
            return f.isEmpty() ? BigDecimal.ZERO : (BigDecimal) f.get(0);
        });
    }

    private BigDecimal saldoCuenta(Long cuentaId) {
        return tx.execute(s -> (BigDecimal) em.createNativeQuery(
                "select saldo from financiero.cuenta_bancaria where id = :id")
                .setParameter("id", cuentaId).getSingleResult());
    }
}
