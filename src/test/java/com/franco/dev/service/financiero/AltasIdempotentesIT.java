package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.EntradaVaria;
import com.franco.dev.domain.financiero.Moneda;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de las altas con clave de idempotencia contra la DB dev real (issue #376). Prueba lo que los mocks no
 * ven: que pedidos simultáneos con la misma clave dejan un solo registro y mueven el saldo una sola vez, que
 * un alta rechazada no deja la clave, y que la numeración de solicitudes de pago se toma en fila.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita commits
 * reales. Las entradas de prueba quedan anuladas (saldo neto cero) y las claves se borran al terminar.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=AltasIdempotentesIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class AltasIdempotentesIT {

    private static final String MARCA = "IT ALTAS IDEMPOTENTES";
    private static final String PREFIJO = "it-altas-";

    @Autowired private AltaIdempotenteService altaIdempotente;
    @Autowired private EntradaVariaService entradaVariaService;
    @Autowired private GastoTesoreriaService gastoTesoreriaService;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long cajaId;
    private Long monedaId;
    private final List<Long> creadas = new ArrayList<>();

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

    @AfterEach
    void limpiar() {
        try {
            for (Long id : creadas) {
                try {
                    entradaVariaService.anular(id, MARCA, null);
                } catch (RuntimeException yaAnulada) {
                    // la anuló la propia prueba; una que no se pueda anular no frena a las demás
                }
            }
        } finally {
            tx.execute(s -> em.createNativeQuery("delete from financiero.operacion_idempotente where clave like :p")
                    .setParameter("p", PREFIJO + "%").executeUpdate());
        }
    }

    private static String clave() {
        return PREFIJO + System.nanoTime();
    }

    private BigDecimal saldoCaja() {
        return tx.execute(s -> (BigDecimal) em.createNativeQuery(
                "select saldo from financiero.caja_virtual_saldo where caja_virtual_id = :c and moneda_id = :m")
                .setParameter("c", cajaId).setParameter("m", monedaId).getSingleResult());
    }

    private long entradasCon(String descripcion) {
        return tx.execute(s -> ((Number) em.createNativeQuery(
                "select count(*) from financiero.entrada_varia where descripcion = :d")
                .setParameter("d", descripcion).getSingleResult()).longValue());
    }

    private long clavesCon(String clave) {
        return tx.execute(s -> ((Number) em.createNativeQuery(
                "select count(*) from financiero.operacion_idempotente where clave = :c")
                .setParameter("c", clave).getSingleResult()).longValue());
    }

    /** El alta de un ingreso de 1, como la arma el resolver: una entidad nueva por pedido. */
    private Supplier<EntradaVaria> ingresoDeUno(String descripcion, String comprobante) {
        return () -> {
            EntradaVaria e = new EntradaVaria();
            e.setCajaVirtual(em.find(CajaVirtual.class, cajaId));
            e.setMoneda(em.find(Moneda.class, monedaId));
            e.setEsIngreso(true);
            e.setMonto(BigDecimal.ONE);
            e.setDescripcion(descripcion);
            e.setNumeroComprobante(comprobante);
            return entradaVariaService.registrar(e, null);
        };
    }

    private EntradaVaria registrar(String clave, String descripcion, String comprobante) {
        EntradaVaria e = altaIdempotente.entradaVaria(clave, "huella-" + descripcion, null, ingresoDeUno(descripcion, comprobante));
        if (!creadas.contains(e.getId())) creadas.add(e.getId());
        return e;
    }

    @Test
    void tresPedidosSimultaneosConLaMismaClaveRegistranUnaSolaEntradaYMuevenElSaldoUnaVez() throws Exception {
        String clave = clave();
        String descripcion = MARCA + " " + clave;
        BigDecimal saldoInicial = saldoCaja();

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<EntradaVaria>> futuros = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futuros.add(pool.submit(() -> {
                largada.await(20, TimeUnit.SECONDS);
                return altaIdempotente.entradaVaria(clave, "huella", null, ingresoDeUno(descripcion, "IT-" + clave));
            }));
        }
        largada.countDown();
        Set<Long> ids = new HashSet<>();
        try {
            for (Future<EntradaVaria> f : futuros) ids.add(f.get(40, TimeUnit.SECONDS).getId());
        } finally {
            pool.shutdownNow();
            creadas.addAll(tx.execute(s -> {
                List<Long> encontradas = new ArrayList<>();
                for (Object id : em.createNativeQuery("select id from financiero.entrada_varia where descripcion = :d")
                        .setParameter("d", descripcion).getResultList()) encontradas.add(((Number) id).longValue());
                return encontradas;
            }));
        }

        assertEquals(1, ids.size(), "los tres pedidos tienen que devolver la misma entrada");
        assertEquals(1, entradasCon(descripcion), "quedó más de una entrada");
        assertEquals(0, saldoInicial.add(BigDecimal.ONE).compareTo(saldoCaja()), "el saldo se movió más de una vez");
    }

    @Test
    void elReintentoDeUnaEntradaConComprobanteTipeadoDevuelveLaOriginalYNoUnYaExiste() {
        String clave = clave();
        String descripcion = MARCA + " " + clave;
        EntradaVaria original = registrar(clave, descripcion, "IT-" + clave);

        EntradaVaria repetida = registrar(clave, descripcion, "IT-" + clave);

        assertEquals(original.getId(), repetida.getId());
        assertNotNull(repetida.getMovimientoCajaVirtualId());
        assertEquals(1, entradasCon(descripcion));
    }

    @Test
    void siElAltaSeRechazaNoQuedaLaClaveNiElMovimientoYElReintentoCorreComoNuevo() {
        String clave = clave();
        String descripcion = MARCA + " " + clave;
        BigDecimal saldoInicial = saldoCaja();

        assertThrows(GraphQLException.class, () -> altaIdempotente.entradaVaria(clave, "huella-" + descripcion, null, () -> {
            ingresoDeUno(descripcion, "").get();   // registra la entrada y mueve el saldo…
            throw new GraphQLException("rechazo a mitad del alta");   // …y después falla
        }));

        assertEquals(0, clavesCon(clave), "la clave quedó aunque el alta se rechazó");
        assertEquals(0, entradasCon(descripcion));
        assertEquals(0, saldoInicial.compareTo(saldoCaja()));

        registrar(clave, descripcion, "");
        assertEquals(1, entradasCon(descripcion));
        assertEquals(1, clavesCon(clave));
    }

    @Test
    void elReintentoDeUnaEntradaQueDespuesSeAnuloSeRechaza() {
        String clave = clave();
        String descripcion = MARCA + " " + clave;
        EntradaVaria original = registrar(clave, descripcion, "");
        entradaVariaService.anular(original.getId(), MARCA, null);

        GraphQLException e = assertThrows(GraphQLException.class, () -> registrar(clave, descripcion, ""));

        assertTrue(e.getMessage().contains("después se anuló"), e.getMessage());
        assertEquals(1, entradasCon(descripcion));
    }

    @Test
    void laMismaClaveConOtroPedidoSeRechaza() {
        String clave = clave();
        String descripcion = MARCA + " " + clave;
        registrar(clave, descripcion, "");

        GraphQLException e = assertThrows(GraphQLException.class, () -> altaIdempotente.entradaVaria(
                clave, "otra-huella", null, ingresoDeUno(descripcion, "")));

        assertTrue(e.getMessage().contains("ya se usó para otro pedido"), e.getMessage());
        assertEquals(1, entradasCon(descripcion));
    }

    @Test
    void variasAltasDeGastoALaVezSalenTodasCadaUnaConSuNumero() throws Exception {
        List<?> tipos = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.tipo_gasto order by id").setMaxResults(1).getResultList());
        assumeTrue(tipos != null && !tipos.isEmpty(), "la base no tiene ningún tipo de gasto");
        Long tipoGastoId = ((Number) tipos.get(0)).longValue();
        String descripcion = MARCA + " " + clave();
        int rondas = 4, porRonda = 3;

        Set<String> numeros = new HashSet<>();
        try {
            for (int ronda = 0; ronda < rondas; ronda++) {
                ExecutorService pool = Executors.newFixedThreadPool(porRonda);
                CountDownLatch largada = new CountDownLatch(1);
                List<Future<String>> futuros = new ArrayList<>();
                for (int i = 0; i < porRonda; i++) {
                    futuros.add(pool.submit(() -> {
                        largada.await(20, TimeUnit.SECONDS);
                        // Sin clave: son pedidos distintos, lo que comparten es el contador de solicitudes.
                        // El lock lo toma el alta (AltaIdempotenteService), no la numeración.
                        return altaIdempotente.gastoParaPago(null, "h", null, () -> gastoTesoreriaService.crearGastoParaPago(
                                tipoGastoId, descripcion, monedaId, 1.0, null, null, null, null, null)).getNumeroSolicitud();
                    }));
                }
                largada.countDown();
                try {
                    // Sin el lock, dos contaban lo mismo y una chocaba contra el índice único del número.
                    for (Future<String> f : futuros) numeros.add(f.get(40, TimeUnit.SECONDS));
                } finally {
                    pool.shutdownNow();
                    pool.awaitTermination(20, TimeUnit.SECONDS);   // que ninguna commitee después del borrado
                }
            }
            assertEquals(rondas * porRonda, numeros.size(), "hubo números de solicitud repetidos");
        } finally {
            // Un gasto no se puede cancelar: los de la prueba se borran (es una sola fila por gasto).
            tx.execute(s -> em.createNativeQuery("delete from operaciones.solicitud_pago where observaciones = :d")
                    .setParameter("d", descripcion).executeUpdate());
        }
    }
}
