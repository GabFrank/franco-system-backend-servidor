package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.service.financiero.GastoTesoreriaService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT del número de las solicitudes de pago contra la DB dev real. Prueba lo que los mocks no ven: que una
 * solicitud borrada del medio ya no traba las altas siguientes, que la solicitud de un vale toma su número
 * llamada sin una transacción exterior, y que el verificador adelanta una secuencia atrasada.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita commits
 * reales. Las solicitudes de prueba se borran al terminar; sus números quedan como huecos.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=SolicitudPagoNumeroIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class SolicitudPagoNumeroIT {

    @Autowired private GastoTesoreriaService gastoTesoreriaService;
    @Autowired private SolicitudPagoService solicitudPagoService;
    @Autowired private SolicitudPagoNumeroVerificador verificador;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long tipoGastoId;
    private Long monedaId;
    private String marca;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        List<?> tipos = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.tipo_gasto order by id").setMaxResults(1).getResultList());
        List<?> monedas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.moneda order by id").setMaxResults(1).getResultList());
        assumeTrue(!tipos.isEmpty() && !monedas.isEmpty(), "la base no tiene tipo de gasto o moneda");
        tipoGastoId = ((Number) tipos.get(0)).longValue();
        monedaId = ((Number) monedas.get(0)).longValue();
        marca = "IT NUMERO SP " + System.nanoTime();
    }

    @AfterEach
    void limpiar() {
        tx.execute(s -> em.createNativeQuery("delete from operaciones.solicitud_pago where observaciones = :d")
                .setParameter("d", marca).executeUpdate());
        // Por si una prueba dejó la secuencia atrasada a propósito.
        verificador.alinear();
    }

    private SolicitudPago gasto() {
        return gastoTesoreriaService.crearGastoParaPago(tipoGastoId, marca, monedaId, 1.0, null, null, null, null, null);
    }

    private static long numeroDe(SolicitudPago s) {
        return Long.parseLong(s.getNumeroSolicitud().substring(3));
    }

    @Test
    void conUnaSolicitudBorradaDelMedioLaSiguienteAltaEntra() {
        SolicitudPago primera = gasto();
        SolicitudPago segunda = gasto();
        // Lo que hace compras al eliminar una solicitud pendiente que no es la última.
        tx.execute(s -> em.createNativeQuery("delete from operaciones.solicitud_pago where id = :id")
                .setParameter("id", primera.getId()).executeUpdate());

        // Contando, el número siguiente era el de la segunda, que ya existe: no entraba ninguna alta más.
        SolicitudPago tercera = gasto();

        assertEquals(numeroDe(segunda) + 1, numeroDe(tercera));
    }

    @Test
    void unaSolicitudDeValeTomaSuNumeroAunqueNadieHayaAbiertoUnaTransaccion() {
        Moneda moneda = tx.execute(s -> em.find(Moneda.class, monedaId));

        // crearSolicitudVale no tiene @Transactional propio: el número tiene que salir igual.
        SolicitudPago solicitud = solicitudPagoService.crearSolicitudVale(moneda, 1.0, marca, null);

        assertTrue(solicitud.getNumeroSolicitud().matches("SP-\\d{6,}"), solicitud.getNumeroSolicitud());
    }

    @Test
    void elVerificadorAdelantaUnaSecuenciaQueQuedoDetrasYNoLaHaceRetroceder() {
        SolicitudPago ultima = gasto();
        // Lo que deja un rollback del JAR: la secuencia detrás de números que ya existen.
        tx.execute(s -> em.createNativeQuery("select setval('operaciones.solicitud_pago_numero_seq', 1, false)").getSingleResult());

        verificador.alinear();
        SolicitudPago siguiente = gasto();
        assertEquals(numeroDe(ultima) + 1, numeroDe(siguiente));

        // Con la secuencia adelante (huecos), arrancar de nuevo no la toca.
        verificador.alinear();
        assertEquals(numeroDe(siguiente) + 1, numeroDe(gasto()));
    }
}
