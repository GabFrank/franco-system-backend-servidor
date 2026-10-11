package com.franco.dev.graphql.administrativo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * El choque de verdad: marcaciones simultaneas contra una base real, con Postgres cancelando
 * transacciones por serializacion. Es lo que los mocks de MarcacionGraphQLReintentoTest no
 * pueden probar.
 *
 * Cada hilo trabaja como una request: con un EntityManager atado al hilo, que es lo que hace
 * OpenEntityManagerInViewFilter en produccion. Asi los intentos de una misma marcacion comparten
 * sesion, y se ve si el segundo arranca limpio despues del rollback del primero.
 *
 * Lo que se exige no es que haya reintentos —dependen del azar— sino que, los haya o no, ninguna
 * marcacion quede con un id repetido ni pisada por otra, y que cada una tenga su jornada.
 *
 * NO corre en CI (no hay base): se activa con -Dit.marcacion=true. Escribe de verdad —no puede
 * ser @Transactional, necesita commits concurrentes— pero en una fecha del anio 2001, donde no
 * hay datos reales, y borra lo suyo al terminar.
 *
 * Va con el perfil dev y no es un detalle: sin perfil, application.properties deja prendidos los
 * schedulers de replicacion, que se conectan a las filiales reales que figuren en la base.
 *
 * Correr:  ./mvnw -DskipFlyway=true -Dit.marcacion=true -Dtest=MarcacionReintentoConcurrenteIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@EnabledIfSystemProperty(named = "it.marcacion", matches = "true")
class MarcacionReintentoConcurrenteIT {

    /** Un dia sin datos reales: todo lo que haya ahi lo creo esta prueba. */
    private static final String DIA = "2001-03-05";
    private static final int HILOS = Integer.getInteger("it.marcacion.hilos", 4);
    private static final int MARCACIONES = Integer.getInteger("it.marcacion.cantidad", 24);

    @Autowired private MarcacionGraphQL resolver;
    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;

    private ListAppender<ILoggingEvent> avisos;
    private Logger logDelResolver;

    @BeforeEach
    void setUp() {
        assumeTrue(contarMarcaciones() == 0 && contarJornadas() == 0,
                "ya hay datos alrededor del " + DIA + " (¿una corrida anterior que se corto?): no se toca nada"
                        + " que esta prueba no haya creado. Revisar y borrar a mano.");
        logDelResolver = (Logger) LoggerFactory.getLogger(MarcacionGraphQL.class);
        avisos = new ListAppender<>();
        avisos.start();
        logDelResolver.addAppender(avisos);
    }

    @AfterEach
    void limpiar() {
        if (logDelResolver != null) {
            logDelResolver.detachAppender(avisos);
            // La jornada referencia a la marcacion: va primero. Se borra con un dia de margen para
            // cada lado —el mismo que se exigio vacio al empezar—: si una jornada cayera en el dia
            // vecino, el borrado de marcaciones fallaria por la clave foranea y quedaria basura.
            jdbc.update("delete from administrativo.jornada where fecha between ?::date - 1 and ?::date + 1", DIA, DIA);
            jdbc.update("delete from administrativo.marcacion where fecha_entrada >= ?::date - 1"
                    + " and fecha_entrada < ?::date + 2", DIA, DIA);
        }
    }

    private int contarMarcaciones() {
        return jdbc.queryForObject(
                "select count(*) from administrativo.marcacion where fecha_entrada >= ?::date - 1"
                        + " and fecha_entrada < ?::date + 2", Integer.class, DIA, DIA);
    }

    private int contarJornadas() {
        return jdbc.queryForObject(
                "select count(*) from administrativo.jornada where fecha between ?::date - 1 and ?::date + 1",
                Integer.class, DIA, DIA);
    }

    /**
     * Guarda una entrada como lo haria una request: con la sesion atada al hilo y con alguien
     * logueado, que es lo unico que SecurityGraphQLAspect le pide a este resolver.
     */
    private Marcacion marcarComoRequest(Long usuarioId, Long sucursalId) {
        EntityManager em = emf.createEntityManager();
        TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(em));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("it-marcacion", "n/a", Collections.emptyList()));
        try {
            MarcacionInput input = new MarcacionInput();
            input.setUsuarioId(usuarioId);
            input.setSucursalId(sucursalId);
            input.setTipo(TipoMarcacion.ENTRADA);
            input.setFechaEntrada(DIA + "T08:00:00");
            return resolver.saveMarcacion(input);
        } finally {
            SecurityContextHolder.clearContext();
            TransactionSynchronizationManager.unbindResource(emf);
            em.close();
        }
    }

    @Test
    void marcacionesSimultaneasNoSePisanNiRepitenId() throws Exception {
        List<Long> usuarios = jdbc.queryForList(
                "select id from personas.usuario where activo is true order by id limit ?", Long.class, MARCACIONES);
        List<Long> sucursales = jdbc.queryForList(
                "select id from empresarial.sucursal where activo is true and deposito is true order by id limit 2",
                Long.class);
        assumeTrue(usuarios.size() == MARCACIONES && sucursales.size() == 2, "faltan usuarios o sucursales de referencia");

        Map<Long, Long> sucursalPedida = new ConcurrentHashMap<>();
        Map<Long, Marcacion> guardadas = new ConcurrentHashMap<>();
        Map<Long, Throwable> fallidas = new ConcurrentHashMap<>();

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        // Se largan de a tandas de HILOS, todas juntas: es lo que provoca el choque.
        for (int desde = 0; desde < usuarios.size(); desde += HILOS) {
            List<Long> tanda = usuarios.subList(desde, Math.min(desde + HILOS, usuarios.size()));
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Void>> enCurso = new ArrayList<>();
            for (int i = 0; i < tanda.size(); i++) {
                Long usuarioId = tanda.get(i);
                Long sucursalId = sucursales.get(i % 2);
                sucursalPedida.put(usuarioId, sucursalId);
                enCurso.add(pool.submit((Callable<Void>) () -> {
                    largada.await();
                    try {
                        guardadas.put(usuarioId, marcarComoRequest(usuarioId, sucursalId));
                    } catch (Throwable t) {
                        fallidas.put(usuarioId, t);
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

        long reintentos = avisos.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
        long rendidas = fallidas.values().stream()
                .filter(t -> MarcacionGraphQL.MENSAJE_CHOQUE.equals(t.getMessage())).count();
        System.out.println("### marcacion-concurrente hilos=" + HILOS + " pedidas=" + usuarios.size()
                + " guardadas=" + guardadas.size() + " reintentos=" + reintentos + " rendidas=" + rendidas
                + " otrosFallos=" + (fallidas.size() - rendidas));
        fallidas.forEach((u, t) -> System.out.println("### fallo usuario=" + u + " " + t));

        // Lo unico que puede salir mal es agotar los intentos, y con su mensaje. Cualquier otra
        // cosa —una sesion rota en el segundo intento, una clave duplicada— es un defecto.
        assertEquals(fallidas.size(), rendidas, "hubo fallos que no son «se agotaron los intentos»: " + fallidas);

        // En la base hay exactamente lo que se informo como guardado, y cada fila es de quien la pidio.
        assertEquals(guardadas.size(), contarMarcaciones(), "filas guardadas");
        for (Map.Entry<Long, Marcacion> g : guardadas.entrySet()) {
            Long usuarioId = g.getKey();
            Marcacion m = g.getValue();
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "select usuario_id, sucursal_id from administrativo.marcacion where id = ? and sucursal_id = ?",
                    m.getId(), sucursalPedida.get(usuarioId));
            assertEquals(1, filas.size(), "la marcación " + m.getId() + " de " + usuarioId);
            assertEquals(usuarioId, ((Number) filas.get(0).get("usuario_id")).longValue(),
                    "la marcación " + m.getId() + " quedó a nombre de otro: fue pisada");
            assertTrue(m.getId() % 2 == 1, "el central asigna ids impares");
        }
        // Y cada marcacion guardada dejo su jornada: un intento fallido no deja media operacion.
        assertEquals(guardadas.size(), contarJornadas(), "jornadas creadas");
        assertEquals(guardadas.size(), jdbc.queryForObject(
                "select count(distinct usuario_id) from administrativo.jornada where fecha = ?::date", Integer.class, DIA),
                "una jornada por persona");
    }
}
