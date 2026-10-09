package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.enums.EstadoChequera;
import com.franco.dev.graphql.financiero.input.ChequeraInput;
import com.franco.dev.repository.financiero.ChequeraRepository;
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
import java.time.LocalDateTime;
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
 * IT de las chequeras contra la DB dev real (issue #376). Prueba lo que los mocks no ven: que guardar una
 * chequera con la fila que la pantalla tenía de antes no hace retroceder el correlativo —el próximo cheque
 * no repite número—, y que dos altas simultáneas con el mismo rango dejan una sola.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita commits
 * reales. Los cheques de prueba quedan anulados y la chequera que crea, anulada.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=ChequerasIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class ChequerasIT {

    private static final String MARCA = "IT CHEQUERAS";

    @Autowired private ChequeraGestionService chequeraGestionService;
    @Autowired private ChequeGestionService chequeGestionService;
    @Autowired private ChequeraRepository chequeraRepository;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
    }

    private Cheque emitirDiferido(Long chequeraId) {
        return tx.execute(s -> {
            Cheque c = new Cheque();
            c.setChequera(chequeraRepository.findById(chequeraId).orElseThrow(IllegalStateException::new));
            c.setTotal(1.0);
            c.setDiferido(true);   // solo reserva; al anularlo se libera
            c.setFechaPago(LocalDateTime.now().plusDays(30));
            c.setConcepto(MARCA);
            return chequeGestionService.emitir(c, null);
        });
    }

    /** La fila tal como la tendría una pantalla: todos sus datos, con el correlativo que se le indique. */
    private ChequeraInput fila(Long chequeraId, Long siguiente) {
        return tx.execute(s -> {
            Chequera c = chequeraRepository.findById(chequeraId).orElseThrow(IllegalStateException::new);
            ChequeraInput in = new ChequeraInput();
            in.setId(c.getId());
            in.setCuentaBancariaId(c.getCuentaBancaria().getId());
            in.setNombre(c.getNombre());
            in.setFirmantes(c.getFirmantes());
            in.setRangoDesde(c.getRangoDesde());
            in.setRangoHasta(c.getRangoHasta());
            in.setSiguienteNumero(siguiente != null ? siguiente : c.getSiguienteNumero());
            in.setEstado(c.getEstado());
            return in;
        });
    }

    @Test
    void guardarLaChequeraConLaFilaViejaNoHaceQueElProximoChequeRepitaNumero() {
        List<?> chequeras = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.chequera where cast(estado as text) = 'ACTIVA' and cuenta_bancaria_id is not null "
                        + "and coalesce(siguiente_numero, rango_desde) + 1 <= rango_hasta order by id")
                .setMaxResults(1).getResultList());
        assumeTrue(!chequeras.isEmpty(), "la base no tiene una chequera activa con números libres");
        Long chequeraId = ((Number) chequeras.get(0)).longValue();

        // La pantalla se carga acá, antes de emitir.
        ChequeraInput pantallaVieja = fila(chequeraId, null);
        List<Cheque> emitidos = new ArrayList<>();
        try {
            Cheque primero = emitirDiferido(chequeraId);
            emitidos.add(primero);

            // Alguien cambia el nombre (o desactiva y reactiva la lista) con esa pantalla: manda el correlativo viejo.
            Chequera guardada = chequeraGestionService.guardar(pantallaVieja, null);
            assertEquals(primero.getNumero().longValue() + 1, guardada.getSiguienteNumero().longValue(),
                    "el correlativo volvió atrás");

            Cheque segundo = emitirDiferido(chequeraId);
            emitidos.add(segundo);
            assertEquals(primero.getNumero() + 1, segundo.getNumero());
        } finally {
            for (Cheque c : emitidos) chequeGestionService.anular(c.getId(), MARCA, null);
        }
    }

    @Test
    void dosAltasALaVezConElMismoRangoDejanUnaSolaChequera() throws Exception {
        List<?> cuentas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.cuenta_bancaria order by id").setMaxResults(1).getResultList());
        assumeTrue(!cuentas.isEmpty(), "la base no tiene ninguna cuenta bancaria");
        Long cuentaId = ((Number) cuentas.get(0)).longValue();
        // Un rango propio de esta corrida, lejos de cualquier chequera real.
        long desde = 800_000_000L + (System.nanoTime() % 1_000_000L) * 100;

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Chequera>> futuros = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futuros.add(pool.submit(() -> {
                ChequeraInput in = new ChequeraInput();
                in.setCuentaBancariaId(cuentaId);
                in.setNombre(MARCA);
                in.setRangoDesde((double) desde);
                in.setRangoHasta((double) (desde + 9));
                largada.await(20, TimeUnit.SECONDS);
                return chequeraGestionService.guardar(in, null);
            }));
        }
        largada.countDown();
        List<Chequera> creadas = new ArrayList<>();
        List<String> rechazos = new ArrayList<>();
        for (Future<Chequera> f : futuros) {
            try {
                creadas.add(f.get(60, TimeUnit.SECONDS));
            } catch (java.util.concurrent.ExecutionException e) {
                assertTrue(e.getCause() instanceof GraphQLException, "error inesperado: " + e.getCause());
                rechazos.add(e.getCause().getMessage());
            }
        }
        pool.shutdownNow();
        try {
            assertEquals(1, creadas.size(), rechazos.toString());
            for (String r : rechazos) assertTrue(r.contains("se superpone con la chequera"), r);
        } finally {
            // Anulada libera el rango (y no se reactiva).
            for (Chequera c : creadas) {
                ChequeraInput baja = fila(c.getId(), null);
                baja.setEstado(EstadoChequera.ANULADA);
                chequeraGestionService.guardar(baja, null);
            }
        }
        // Con la primera anulada, el mismo rango vuelve a estar libre.
        ChequeraInput otra = new ChequeraInput();
        otra.setCuentaBancariaId(cuentaId);
        otra.setNombre(MARCA);
        otra.setRangoDesde((double) desde);
        otra.setRangoHasta((double) (desde + 9));
        Chequera segunda = chequeraGestionService.guardar(otra, null);
        ChequeraInput baja = fila(segunda.getId(), null);
        baja.setEstado(EstadoChequera.ANULADA);
        assertEquals(EstadoChequera.ANULADA, chequeraGestionService.guardar(baja, null).getEstado());

        // Y una anulada no vuelve: la pantalla vieja que la tenía activa no la reactiva.
        ChequeraInput reactivar = fila(segunda.getId(), null);
        reactivar.setEstado(EstadoChequera.ACTIVA);
        assertThrows(GraphQLException.class, () -> chequeraGestionService.guardar(reactivar, null));
    }
}
