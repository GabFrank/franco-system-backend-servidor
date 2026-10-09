package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.EntradaVaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de las validaciones que pasaron al central (issue #376), contra la DB dev real. Prueba lo que los
 * mocks no ven: el lock por nombre (pg_advisory_xact_lock) contra PostgreSQL, que el cierre de un maletín
 * y un número de comprobante entran una sola vez con pedidos simultáneos, y que dos emisiones a la vez de
 * la misma chequera salen con números distintos aunque las dos la tengan cargada con el número viejo.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita commits
 * reales. Lo que registra lo anula al terminar; los cheques de prueba quedan anulados.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=ValidacionesFinancieroIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class ValidacionesFinancieroIT {

    private static final String MARCA = "IT VALIDACIONES";

    @Autowired private MaletinTesoreriaService maletinTesoreriaService;
    @Autowired private EntradaVariaService entradaVariaService;
    @Autowired private ChequeGestionService chequeGestionService;
    @Autowired private ChequeraRepository chequeraRepository;
    @Autowired private TesoreriaService tesoreriaService;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long cajaMayorId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // Una caja de prueba (inactiva) si hay.
        List<?> cajas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.caja_virtual order by coalesce(activo, true), id").setMaxResults(1).getResultList());
        assumeTrue(!cajas.isEmpty(), "la base no tiene ninguna caja mayor");
        cajaMayorId = ((Number) cajas.get(0)).longValue();
    }

    /** Lanza las acciones a la vez. Devuelve los resultados de las que pasaron y deja los rechazos en {@code rechazos}. */
    private <T> List<T> aLaVez(List<Callable<T>> acciones, List<String> rechazos) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(acciones.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> accion : acciones) {
                futuros.add(pool.submit(() -> {
                    largada.await(20, TimeUnit.SECONDS);
                    return accion.call();
                }));
            }
            largada.countDown();
            List<T> exitos = new ArrayList<>();
            for (Future<T> f : futuros) {
                try {
                    exitos.add(f.get(60, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException e) {
                    assertTrue(e.getCause() instanceof GraphQLException, "error inesperado: " + e.getCause());
                    rechazos.add(e.getCause().getMessage());
                }
            }
            return exitos;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void elCierreDeUnMaletinEntraUnaSolaVezAunqueSePidaSeisVecesALaVez() throws Exception {
        // Un maletín cuya última caja de PDV tiene conteo de cierre con valores.
        List<?> maletines = tx.execute(s -> em.createNativeQuery(
                "select u.maletin_id from (select distinct on (maletin_id) maletin_id, sucursal_id, conteo_cierre_id "
                        + "from financiero.pdv_caja where maletin_id is not null order by maletin_id, creado_en desc) u "
                        + "where u.conteo_cierre_id is not null and exists (select 1 from financiero.conteo_moneda cm "
                        + "where cm.conteo_id = u.conteo_cierre_id and cm.sucursal_id = u.sucursal_id and cm.cantidad > 0) "
                        + "order by u.maletin_id").setMaxResults(1).getResultList());
        assumeTrue(!maletines.isEmpty(), "la base no tiene ningún maletín con un cierre con valores");
        Long maletinId = ((Number) maletines.get(0)).longValue();
        assumeTrue(ingresadas(maletinId).isEmpty(), "el cierre de ese maletín ya está ingresado");

        List<Callable<List<MovimientoCajaVirtual>>> acciones = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            acciones.add(() -> maletinTesoreriaService.ingresarMaletinCierre(cajaMayorId, maletinId, null, MARCA, null));
        }
        List<String> rechazos = new ArrayList<>();
        List<List<MovimientoCajaVirtual>> exitos = aLaVez(acciones, rechazos);
        try {
            assertEquals(1, exitos.size(), rechazos.toString());
            for (String r : rechazos) assertTrue(r.contains("ya se ingresó"), r);
            assertEquals(exitos.get(0).size(), ingresadas(maletinId).size(), "cada moneda ingresada queda marcada");
        } finally {
            // Anulado, el cierre queda otra vez sin ingresar.
            for (List<MovimientoCajaVirtual> creados : exitos) {
                for (int i = creados.size() - 1; i >= 0; i--) tesoreriaService.anular(creados.get(i).getId(), MARCA, null);
            }
        }
        assertTrue(ingresadas(maletinId).isEmpty());
    }

    /** Monedas que valorMaletin da por ingresadas. En una transacción: el servicio navega relaciones lazy. */
    private List<Long> ingresadas(Long maletinId) {
        return tx.execute(s -> {
            List<Long> monedas = new ArrayList<>();
            for (MaletinTesoreriaService.ValorMaletinItem v : maletinTesoreriaService.valorMaletin(maletinId)) {
                if (Boolean.TRUE.equals(v.getIngresado())) monedas.add(v.getMoneda().getId());
            }
            return monedas;
        });
    }

    @Test
    void dosEntradasVariasConElMismoComprobanteALaVezDejanUnaSola() throws Exception {
        Long monedaId = tx.execute(s -> ((Number) em.createNativeQuery(
                "select id from financiero.moneda order by id").setMaxResults(1).getSingleResult()).longValue());
        String comprobante = "it-val-" + System.nanoTime();

        List<Callable<EntradaVaria>> acciones = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            acciones.add(() -> {
                EntradaVaria e = new EntradaVaria();
                e.setCajaVirtual(tx.execute(s -> em.find(CajaVirtual.class, cajaMayorId)));
                e.setMoneda(tx.execute(s -> em.find(Moneda.class, monedaId)));
                e.setEsIngreso(true);
                e.setMonto(BigDecimal.ONE);
                e.setDescripcion(MARCA);
                e.setNumeroComprobante("  " + comprobante + " ");
                return entradaVariaService.registrar(e, null);
            });
        }
        List<String> rechazos = new ArrayList<>();
        List<EntradaVaria> exitos = aLaVez(acciones, rechazos);
        try {
            assertEquals(1, exitos.size(), rechazos.toString());
            assertEquals(comprobante.toUpperCase(), exitos.get(0).getNumeroComprobante());
            for (String r : rechazos) assertTrue(r.contains("Ya existe una entrada varia con el comprobante"), r);
        } finally {
            for (EntradaVaria e : exitos) entradaVariaService.anular(e.getId(), MARCA, null);
        }
        // Anulada, el número queda libre: otra entrada con el mismo comprobante pasa.
        EntradaVaria otra = new EntradaVaria();
        otra.setCajaVirtual(tx.execute(s -> em.find(CajaVirtual.class, cajaMayorId)));
        otra.setMoneda(tx.execute(s -> em.find(Moneda.class, monedaId)));
        otra.setEsIngreso(true);
        otra.setMonto(BigDecimal.ONE);
        otra.setDescripcion(MARCA);
        otra.setNumeroComprobante(comprobante);
        EntradaVaria creada = entradaVariaService.registrar(otra, null);
        entradaVariaService.anular(creada.getId(), MARCA, null);
    }

    @Test
    void dosEmisionesALaVezDeLaMismaChequeraSalenConNumerosDistintos() throws Exception {
        // Una chequera activa con al menos dos números libres en su rango.
        List<?> chequeras = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.chequera where cast(estado as text) = 'ACTIVA' and cuenta_bancaria_id is not null "
                        + "and coalesce(siguiente_numero, rango_desde) + 1 <= rango_hasta order by id")
                .setMaxResults(1).getResultList());
        assumeTrue(!chequeras.isEmpty(), "la base no tiene una chequera activa con números libres");
        Long chequeraId = ((Number) chequeras.get(0)).longValue();

        // Las dos transacciones cargan la chequera ANTES de emitir —como hacen el resolver y el pago a
        // proveedores— y recién cuando las dos la tienen, emiten: la segunda espera el lock con el número viejo.
        CyclicBarrier lasDosLaCargaron = new CyclicBarrier(2);
        List<Callable<Cheque>> acciones = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            acciones.add(() -> tx.execute(s -> {
                Chequera cargada = chequeraRepository.findById(chequeraId).orElseThrow(IllegalStateException::new);
                try {
                    lasDosLaCargaron.await(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                Cheque c = new Cheque();
                c.setChequera(cargada);
                c.setTotal(1.0);
                c.setDiferido(true);   // diferido: solo reserva, y al anularlo se libera
                c.setFechaPago(LocalDateTime.now().plusDays(30));
                c.setConcepto(MARCA);
                return chequeGestionService.emitir(c, null);
            }));
        }
        List<String> rechazos = new ArrayList<>();
        List<Cheque> emitidos = aLaVez(acciones, rechazos);
        try {
            assertEquals(2, emitidos.size(), rechazos.toString());
            Set<Double> numeros = new HashSet<>();
            for (Cheque c : emitidos) numeros.add(c.getNumero());
            assertEquals(2, numeros.size(), "las dos emisiones salieron con el mismo número: " + numeros);
        } finally {
            for (Cheque c : emitidos) chequeGestionService.anular(c.getId(), MARCA, null);
        }
    }

    @Test
    void dosChequesDeLaMismaChequeraEnUnaSolaTransaccionSalenConNumerosConsecutivos() {
        // Como un pago a proveedores con dos cheques: la chequera queda modificada por el primero y sin
        // volcar cuando el segundo la toma. El lock la vuelca antes de releerla; si no, el refresh pisaría
        // el avance y el segundo repetiría el número.
        List<?> chequeras = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.chequera where cast(estado as text) = 'ACTIVA' and cuenta_bancaria_id is not null "
                        + "and coalesce(siguiente_numero, rango_desde) + 1 <= rango_hasta order by id")
                .setMaxResults(1).getResultList());
        assumeTrue(!chequeras.isEmpty(), "la base no tiene una chequera activa con números libres");
        Long chequeraId = ((Number) chequeras.get(0)).longValue();

        List<Cheque> emitidos = tx.execute(s -> {
            List<Cheque> lista = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Cheque c = new Cheque();
                c.setChequera(chequeraRepository.findById(chequeraId).orElseThrow(IllegalStateException::new));
                c.setTotal(1.0);
                c.setDiferido(true);
                c.setFechaPago(LocalDateTime.now().plusDays(30));
                c.setConcepto(MARCA);
                lista.add(chequeGestionService.emitir(c, null));
            }
            return lista;
        });
        try {
            assertEquals(emitidos.get(0).getNumero() + 1, emitidos.get(1).getNumero());
        } finally {
            for (Cheque c : emitidos) chequeGestionService.anular(c.getId(), MARCA, null);
        }
    }
}
