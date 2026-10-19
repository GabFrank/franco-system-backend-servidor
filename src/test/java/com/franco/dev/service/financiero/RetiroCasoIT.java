package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.RetiroCaso;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.financiero.enums.VeredictoCasoRetiro;
import com.franco.dev.domain.personas.Usuario;
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
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de los casos de retiro contra la DB dev real (issue #376). Prueba lo que los mocks no ven: que
 * resolver un caso y anular su verificación van en una sola transacción, y que una anulación directa no
 * le pisa el veredicto a una resolución que corre a la vez.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true.
 * No es @Transactional: necesita commits reales. Cada prueba verifica un retiro de la base local que
 * esté sin ingresar, contra una caja propia («IT CASO …»), y al terminar anula la verificación si quedó
 * vigente —el retiro vuelve a flotar— y desactiva la caja. Los casos quedan, resueltos.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=RetiroCasoIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class RetiroCasoIT {

    private static final String MARCA = "IT CASO";

    @Autowired private RetiroCasoService casoService;
    @Autowired private RetiroVerificacionService verificacionService;
    @Autowired private CajaVirtualService cajaVirtualService;
    @Autowired private TesoreriaService tesoreriaService;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long cajaId;
    private Long monedaId;
    private Long verificacionId;
    private Long casoId;
    private Usuario investigador;
    private Long personaId;
    private VeredictoCasoRetiro veredictoPosible;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);

        List<Object[]> retiros = nativa("select r.id, r.sucursal_id from financiero.retiro r "
                + "where r.movimiento_caja_virtual_id is null "
                + "and exists (select 1 from financiero.retiro_detalle d where d.retiro_id = r.id "
                + "            and d.sucursal_id = r.sucursal_id and d.cantidad > 1) "
                + "and not exists (select 1 from financiero.retiro_verificacion v where v.retiro_id = r.id "
                + "                and v.sucursal_id = r.sucursal_id and v.anulada = false) "
                + "order by r.id desc limit 1");
        assumeTrue(!retiros.isEmpty(), "la base no tiene ningún retiro sin ingresar");
        Long retiroId = ((Number) retiros.get(0)[0]).longValue();
        Long sucursalId = ((Number) retiros.get(0)[1]).longValue();

        List<?> usuarios = tx.execute(s -> em.createQuery("select u from Usuario u order by u.id", Usuario.class)
                .setMaxResults(1).getResultList());
        List<?> personas = tx.execute(s -> em.createQuery("select p.id from Persona p order by p.id")
                .setMaxResults(1).getResultList());
        List<?> monedas = tx.execute(s -> em.createQuery("select m.id from Moneda m order by m.id")
                .setMaxResults(1).getResultList());
        assumeTrue(!usuarios.isEmpty() && !personas.isEmpty() && !monedas.isEmpty(), "faltan datos de referencia");
        investigador = (Usuario) usuarios.get(0);
        personaId = (Long) personas.get(0);
        monedaId = (Long) monedas.get(0);

        CajaVirtual caja = new CajaVirtual();
        caja.setNombre(MARCA + " " + System.nanoTime());
        caja.setTipo(CajaVirtualTipo.CAJA_MAYOR);
        caja.setPermiteSaldoNegativo(false);
        cajaId = cajaVirtualService.save(caja).getId();

        // Se cuenta 1 en la primera moneda: el retiro declara más que eso, así que hay diferencia y se
        // abre un caso. En la caja queda acreditado exactamente 1.
        RetiroVerificacionService.ConteoMoneda conteo = new RetiroVerificacionService.ConteoMoneda();
        conteo.setMonedaId(monedaId);
        conteo.setContado(BigDecimal.ONE);
        RetiroVerificacion v = verificacionService.verificar(retiroId, sucursalId, cajaId,
                Collections.singletonList(conteo), false, MARCA, null);
        verificacionId = v.getId();

        List<Object[]> caso = nativa("select c.id, "
                + "(select count(*) from financiero.retiro_verificacion_detalle d where d.verificacion_id = c.verificacion_id and d.diferencia < 0) "
                + "from financiero.retiro_caso c where c.verificacion_id = " + verificacionId);
        assumeTrue(!caso.isEmpty(), "la verificación no abrió un caso");
        casoId = ((Number) caso.get(0)[0]).longValue();
        veredictoPosible = ((Number) caso.get(0)[1]).longValue() > 0
                ? VeredictoCasoRetiro.FALTANTE_PDV : VeredictoCasoRetiro.SOBRANTE_PDV;

        casoService.asignar(casoId, investigador.getId(), false);
    }

    @AfterEach
    void limpiar() {
        if (verificacionId != null && !verificacionAnulada()) {
            try {
                if (saldoCaja().signum() <= 0) moverCaja(CajaVirtualTipoMovimiento.INGRESO);
                verificacionService.anular(verificacionId, MARCA + " LIMPIEZA", null);
            } catch (RuntimeException e) {
                System.err.println("RetiroCasoIT: no se pudo anular la verificación " + verificacionId + ": " + e);
            }
        }
        if (cajaId != null) {
            tx.execute(s -> em.createNativeQuery("update financiero.caja_virtual set activo = false where id = :id")
                    .setParameter("id", cajaId).executeUpdate());
        }
    }

    // ── helpers ──

    @SuppressWarnings("unchecked")
    private List<Object[]> nativa(String sql) {
        return tx.execute(s -> (List<Object[]>) em.createNativeQuery(sql).getResultList());
    }

    private Object[] casoEnLaBase() {
        return nativa("select cast(c.estado as text), cast(c.veredicto as text), c.resolucion, c.resuelto_por, c.asignado_a "
                + "from financiero.retiro_caso c where c.id = " + casoId).get(0);
    }

    private boolean verificacionAnulada() {
        List<?> r = tx.execute(s -> em.createNativeQuery(
                "select anulada from financiero.retiro_verificacion where id = " + verificacionId).getResultList());
        return Boolean.TRUE.equals(r.get(0));
    }

    private BigDecimal saldoCaja() {
        List<?> r = tx.execute(s -> em.createNativeQuery(
                "select saldo from financiero.caja_virtual_saldo where caja_virtual_id = :c and moneda_id = :m")
                .setParameter("c", cajaId).setParameter("m", monedaId).getResultList());
        return r.isEmpty() ? BigDecimal.ZERO : (BigDecimal) r.get(0);
    }

    /** Mueve 1 en la caja de la prueba: sacarlo deja a la verificación sin poder anularse. */
    private void moverCaja(CajaVirtualTipoMovimiento tipo) {
        tx.execute(s -> {
            MovimientoCajaVirtual m = new MovimientoCajaVirtual();
            m.setCajaVirtual(em.getReference(CajaVirtual.class, cajaId));
            m.setMoneda(em.getReference(Moneda.class, monedaId));
            m.setTipoMovimiento(tipo);
            m.setCantidad(1.0);
            m.setDescripcion(MARCA);
            m.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
            m.setActivo(true);
            return tesoreriaService.registrar(m).getId();
        });
    }

    private RetiroCaso resolverAnulando() {
        return casoService.resolver(casoId, VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA, MARCA + " informe",
                personaId, null, true, investigador, false);
    }

    private RetiroCaso resolverSinAnular() {
        return casoService.resolver(casoId, veredictoPosible, MARCA + " informe", personaId, null, false,
                investigador, false);
    }

    // ── pruebas ──

    @Test
    void siLaAnulacionNoPasaElCasoNoQuedaResuelto() {
        moverCaja(CajaVirtualTipoMovimiento.EGRESO);   // la caja ya no tiene lo acreditado

        GraphQLException e = assertThrows(GraphQLException.class, this::resolverAnulando);
        assertTrue(e.getMessage().contains("Saldo insuficiente"), e.getMessage());

        Object[] caso = casoEnLaBase();
        assertEquals("EN_INVESTIGACION", caso[0], "el caso quedó resuelto aunque la anulación falló");
        assertNull(caso[1], "quedó un veredicto guardado");
        assertNull(caso[3]);
        assertFalse(verificacionAnulada());

        // Y se puede volver a resolver: con la plata de vuelta en la caja, la misma resolución pasa.
        moverCaja(CajaVirtualTipoMovimiento.INGRESO);
        resolverAnulando();

        caso = casoEnLaBase();
        assertEquals("RESUELTO", caso[0]);
        assertEquals("ERROR_DE_CONTEO_TESORERIA", caso[1]);
        assertTrue(verificacionAnulada());
        assertEquals(0, saldoCaja().signum(), "lo acreditado no volvió a salir de la caja");
    }

    @Test
    void unaAnulacionDirectaNoLePisaElVeredictoAUnaResolucionQueCorreALaVez() throws Exception {
        CountDownLatch resolvioSinCommitear = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // La resolución queda con su transacción abierta: tiene el caso tomado y ya lo escribió.
            Future<?> resolucion = pool.submit(() -> tx.execute(s -> {
                resolverSinAnular();
                em.flush();
                resolvioSinCommitear.countDown();
                try {
                    soltar.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertTrue(resolvioSinCommitear.await(30, TimeUnit.SECONDS), "la resolución no llegó a escribir");

            Future<?> anulacion = pool.submit(() -> verificacionService.anular(verificacionId, MARCA, null));
            // La anulación revierte la plata y se queda esperando el caso.
            Thread.sleep(2000);
            assertFalse(anulacion.isDone(), "la anulación no esperó a la resolución");

            soltar.countDown();
            resolucion.get(30, TimeUnit.SECONDS);
            anulacion.get(30, TimeUnit.SECONDS);
        } finally {
            soltar.countDown();
            pool.shutdownNow();
        }

        Object[] caso = casoEnLaBase();
        assertEquals("RESUELTO", caso[0]);
        assertEquals(veredictoPosible.name(), caso[1], "la anulación pisó el veredicto");
        assertEquals((MARCA + " informe").toUpperCase(), caso[2], "la anulación pisó el informe");
        assertEquals(investigador.getId(), ((Number) caso[3]).longValue());
        assertTrue(verificacionAnulada());
    }

    @Test
    void dosResolucionesALaVezDejanPasarUnaSola() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<RetiroCaso>> futuros = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await(20, TimeUnit.SECONDS);
                    return resolverSinAnular();
                }));
            }
            largada.countDown();
            int exitos = 0, rechazos = 0;
            for (Future<RetiroCaso> f : futuros) {
                try {
                    f.get(40, TimeUnit.SECONDS);
                    exitos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertTrue(e.getCause() instanceof GraphQLException
                            && "El caso ya está resuelto".equals(e.getCause().getMessage()), "rechazo inesperado: " + e.getCause());
                    rechazos++;
                }
            }
            assertEquals(1, exitos);
            assertEquals(2, rechazos);
        } finally {
            pool.shutdownNow();
        }
        assertEquals("RESUELTO", casoEnLaBase()[0]);
    }

    @Test
    void asignarNoReabreUnCasoResuelto() {
        resolverSinAnular();

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> casoService.asignar(casoId, investigador.getId(), true));

        assertEquals("El caso ya está resuelto", e.getMessage());
        assertEquals("RESUELTO", casoEnLaBase()[0]);
    }

    @Test
    void anularLaVerificacionCierraSinVeredictoElCasoQueSeguiaAbierto() {
        verificacionService.anular(verificacionId, MARCA, investigador);

        Object[] caso = casoEnLaBase();
        assertEquals("RESUELTO", caso[0]);
        assertNull(caso[1], "un cierre por anulación no lleva veredicto");
        assertEquals("CERRADO POR ANULACION DE LA VERIFICACION: " + MARCA, caso[2]);
        assertEquals(investigador.getId(), ((Number) caso[3]).longValue());
        assertEquals(investigador.getId(), ((Number) caso[4]).longValue(), "el cierre no debe tocar a quién estaba asignado");
        assertTrue(verificacionAnulada());
    }
}
