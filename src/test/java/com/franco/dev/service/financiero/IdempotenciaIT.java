package com.franco.dev.service.financiero;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT de {@link IdempotenciaService} contra la DB dev real (issue #376). Es la única prueba automática
 * de lo que el diseño le pide a PostgreSQL: que la clave se vaya con el rollback y que dos pedidos
 * simultáneos con la misma clave se serialicen en la clave primaria.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true.
 * No es @Transactional: necesita commits reales. Solo escribe en financiero.operacion_idempotente,
 * con claves propias (prefijo it-idem-) que borra al terminar.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=IdempotenciaIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class IdempotenciaIT {

    private static final String PREFIJO = "it-idem-";
    private static final String OPERACION = "IT_OPERACION";
    private static final String HUELLA = new HuellaPedido().texto("pedido").calcular();

    @Autowired private IdempotenciaService service;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Usuario usuario;
    private String clave;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        usuario = new Usuario();
        usuario.setId(1L);
        clave = PREFIJO + java.util.UUID.randomUUID();
        limpiar();
    }

    @AfterEach
    void limpiar() {
        tx.execute(s -> em.createNativeQuery(
                "DELETE FROM financiero.operacion_idempotente WHERE clave LIKE :p")
                .setParameter("p", PREFIJO + "%").executeUpdate());
    }

    private long ejecutar(String clave, String operacion, String huella, Usuario usuario, Supplier<Long> accion) {
        return tx.execute(s -> service.ejecutar(clave, operacion, huella, usuario, accion,
                Function.identity(), id -> id));
    }

    @Test
    void claveNuevaEjecutaYLaRepetidaDevuelveLoYaCreadoSinEjecutar() {
        AtomicInteger veces = new AtomicInteger();
        assertEquals(77L, ejecutar(clave, OPERACION, HUELLA, usuario, () -> { veces.incrementAndGet(); return 77L; }));
        assertEquals(77L, ejecutar(clave, OPERACION, HUELLA, usuario, () -> { veces.incrementAndGet(); return 99L; }));
        assertEquals(1, veces.get());
    }

    @Test
    void laClaveSeRecortaAntesDeGuardarse() {
        assertEquals(77L, ejecutar("  " + clave + " ", OPERACION, HUELLA, usuario, () -> 77L));
        assertEquals(77L, ejecutar(clave, OPERACION, HUELLA, usuario, () -> 99L));
    }

    @Test
    void sinClaveEjecutaSiempre() {
        AtomicInteger veces = new AtomicInteger();
        ejecutar(null, OPERACION, HUELLA, usuario, () -> (long) veces.incrementAndGet());
        ejecutar("  ", OPERACION, HUELLA, usuario, () -> (long) veces.incrementAndGet());
        assertEquals(2, veces.get());
    }

    @Test
    void claveUsadaParaOtroPedidoSeRechazaSinEjecutar() {
        ejecutar(clave, OPERACION, HUELLA, usuario, () -> 77L);
        AtomicInteger veces = new AtomicInteger();
        Supplier<Long> otra = () -> (long) veces.incrementAndGet();
        Usuario otroUsuario = new Usuario();
        otroUsuario.setId(2L);

        String otraHuella = new HuellaPedido().texto("otro pedido").calcular();
        assertThrows(GraphQLException.class, () -> ejecutar(clave, OPERACION, otraHuella, usuario, otra));
        assertThrows(GraphQLException.class, () -> ejecutar(clave, "IT_OTRA", HUELLA, usuario, otra));
        assertThrows(GraphQLException.class, () -> ejecutar(clave, OPERACION, HUELLA, otroUsuario, otra));
        assertThrows(GraphQLException.class, () -> ejecutar(clave, OPERACION, HUELLA, null, otra));
        assertEquals(0, veces.get());
    }

    @Test
    void accionQueFallaNoDejaLaClave() {
        assertThrows(GraphQLException.class, () -> ejecutar(clave, OPERACION, HUELLA, usuario,
                () -> { throw new GraphQLException("rechazo de negocio"); }));
        // El reintento, ya corregido, corre como un pedido nuevo — incluso con otro contenido.
        String corregida = new HuellaPedido().texto("pedido corregido").calcular();
        assertEquals(78L, ejecutar(clave, OPERACION, corregida, usuario, () -> 78L));
    }

    @Test
    void claveSinResultadoSeRechazaYNoDevuelveVacio() {
        tx.execute(s -> em.createNativeQuery(
                        "INSERT INTO financiero.operacion_idempotente (clave, operacion, usuario_id, huella) " +
                        "VALUES (:c, :o, 1, :h)")
                .setParameter("c", clave).setParameter("o", OPERACION).setParameter("h", HUELLA).executeUpdate());
        assertThrows(GraphQLException.class, () -> ejecutar(clave, OPERACION, HUELLA, usuario, () -> 77L));
    }

    @Test
    void claveDemasiadoLargaSeRechaza() {
        StringBuilder larga = new StringBuilder(PREFIJO);
        while (larga.length() <= IdempotenciaService.LARGO_MAXIMO_CLAVE) larga.append('x');
        assertThrows(GraphQLException.class, () -> ejecutar(larga.toString(), OPERACION, HUELLA, usuario, () -> 77L));
    }

    @Test
    void dosPedidosSimultaneosConLaMismaClaveEjecutanUnaSolaVez() throws Exception {
        AtomicInteger veces = new AtomicInteger();
        CountDownLatch primeroAdentro = new CountDownLatch(1);
        CountDownLatch soltarPrimero = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Long> primero = pool.submit(() -> ejecutar(clave, OPERACION, HUELLA, usuario, () -> {
                veces.incrementAndGet();
                primeroAdentro.countDown();
                try {
                    soltarPrimero.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 77L;
            }));
            assertTrue(primeroAdentro.await(20, TimeUnit.SECONDS), "el primer pedido no llegó a ejecutar");

            Future<Long> segundo = pool.submit(() -> ejecutar(clave, OPERACION, HUELLA, usuario,
                    () -> { veces.incrementAndGet(); return 99L; }));
            // El segundo tiene que estar esperando en la clave primaria, no ejecutando ni terminado.
            Thread.sleep(1500);
            assertFalse(segundo.isDone(), "el segundo pedido no esperó al primero");
            assertEquals(1, veces.get());

            soltarPrimero.countDown();
            assertEquals(77L, primero.get(20, TimeUnit.SECONDS));
            assertEquals(77L, segundo.get(20, TimeUnit.SECONDS));
            assertEquals(1, veces.get());
        } finally {
            soltarPrimero.countDown();
            pool.shutdownNow();
        }
    }
}
