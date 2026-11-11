package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #609: el filtro por estado (y por caja anterior) del analisis de diferencias se resuelve en la base
 * y se pagina despues. Antes se filtraba en memoria la pagina ya cortada: la pagina 2 salia vacia y el
 * total era el de la pagina. Verifica tambien que el estado de la consulta SQL es el mismo que pone
 * Java (enriquecerConCajaAnterior + estadoPorMoneda) en cada caja sembrada.
 *
 * Corre contra una base real con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.analisisDiferencias=true -Dtest=AnalisisDiferenciasQueriesIT
 *
 * Igual que ProductoStockPorSignoQueriesIT: Flyway apagado, transaccional (lo sembrado se revierte),
 * sin contexto web y con la replica apagada. Siembra en 2001, donde la base no tiene cajas.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "replication.sync.enabled=false",
                "replication.refresh.enabled=false"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@EnabledIfSystemProperty(named = "it.analisisDiferencias", matches = "true")
class AnalisisDiferenciasQueriesIT {

    private static final long SUC = 1L;
    private static final long MALETIN = 999609001L;
    private static final long C1 = 999609101L, C2 = 999609103L, C3 = 999609105L, C4 = 999609107L,
            C5 = 999609109L, C6 = 999609111L, C7 = 999609113L, C8 = 999609115L, C9 = 999609117L;
    // billetes existentes: GUARANI 1000/2000/5000, REAL 0,05/0,1/0,25/10, DOLAR 10
    private static final long GS1000 = 2, GS2000 = 3, GS5000 = 4, R005 = 9, R01 = 10, R025 = 11, R10 = 16, US10 = 23;
    private static final String DESDE = "2001-01-01T00:00:00", HASTA = "2001-01-03T00:00:00";

    @Autowired private PdvCajaService service;
    @Autowired private PdvCajaRepository repository;
    @Autowired private EntityManager em;

    private long conteoId = 999609501L, conteoMonedaId = 999609701L;

    @BeforeEach
    void sembrar() {
        sql("insert into financiero.maletin (id, descripcion, activo) values (" + MALETIN + ", 'ZZ609 MALETIN', true)");
        // C1 abre el maletin: sin caja anterior -> SIN_DATOS
        caja(C1, MALETIN, "08:00", "12:00", "08:00", conteo(m(GS1000, 1)), conteo(m(GS1000, 1), m(R10, 1)));
        // C2 abre con lo que cerro C1 -> SIN
        caja(C2, MALETIN, "13:00", "14:00", "13:00", conteo(m(GS1000, 1), m(R10, 1)), conteo(m(R10, 1)));
        // C3: los R$ 10 volvieron como US$ 10 -> CON
        caja(C3, MALETIN, "15:00", "16:00", "15:00", conteo(m(US10, 1)), conteo(m(R01, 3)));
        // C4: 3 x 0,1 contra 0,25 + 0,05 -> SIN (con double, 0,30000000000000004 contra 0,3)
        caja(C4, MALETIN, "17:00", "18:00", "17:00", conteo(m(R025, 1), m(R005, 1)), conteo(m(GS5000, 1)));
        // C5: sin conteo de apertura -> SIN_DATOS
        caja(C5, MALETIN, "19:00", null, "19:00", null, null);
        // C6: anterior C4 (C5 no cerro); un billete con cantidad nula se ignora -> SIN
        caja(C6, MALETIN, "19:30", "20:00", "19:30", conteo(m(GS5000, 1), m(GS1000, null)), conteo(m(GS1000, 1)));
        // C7: anterior C4 (C6 todavia abierta) -> SIN; cierra en el mismo instante que C6
        caja(C7, MALETIN, "19:45", "20:00", "19:45", conteo(m(GS5000, 1)), conteo(m(GS2000, 1)));
        // C8: dos anteriores cerradas a las 20:00; gana la de id mayor (C7: Gs 2000) -> SIN.
        // Mismo creado_en que C7: el orden lo decide el id.
        caja(C8, MALETIN, "21:00", null, "19:45", conteo(m(GS2000, 1)), null);
        // C9: sin maletin -> SIN_DATOS
        caja(C9, null, "09:00", null, "23:00", conteo(m(GS1000, 1)), null);
        sql("update financiero.pdv_caja set verificado = true where id = " + C2);
        sql("update financiero.pdv_caja set estado = 'CONCLUIDO' where id in (" + C3 + "," + C4 + ")");
        em.flush();
        em.clear();
    }

    @Test
    void estadoDeCadaCajaSqlIgualAJava() {
        Object[][] esperado = {
                {C1, "SIN_DATOS"}, {C2, "SIN_DIFERENCIA"}, {C3, "CON_DIFERENCIA"}, {C4, "SIN_DIFERENCIA"},
                {C5, "SIN_DATOS"}, {C6, "SIN_DIFERENCIA"}, {C7, "SIN_DIFERENCIA"}, {C8, "SIN_DIFERENCIA"},
                {C9, "SIN_DATOS"},
        };
        for (Object[] e : esperado) {
            long id = (Long) e[0];
            String sql = estadoSql(id);
            Page<PdvCaja> sinFiltro = service.findAllForAnalisisDiferencias(id, null, null, null, null, null,
                    null, null, SUC, null, null, PageRequest.of(0, 10));
            String java = sinFiltro.getContent().get(0).getEstadoDiferenciaConsecutiva();
            assertEquals(e[1], sql, "SQL, caja " + id);
            assertEquals(e[1], java, "Java, caja " + id);
        }
    }

    @Test
    void cajaAnteriorConCierresEmpatadosEsLaDeIdMayorEnLosDosLados() {
        Page<PdvCaja> p = service.findAllForAnalisisDiferencias(C8, null, null, null, null, null,
                null, null, SUC, null, null, PageRequest.of(0, 10));
        assertEquals(C7, p.getContent().get(0).getCajaAnteriorId());
        assertEquals(ids(C8), ids(porAnterior(C7)));
        assertEquals(ids(), ids(porAnterior(C6)));
    }

    @Test
    void sinDiferenciaPaginadoSinPerderNiRepetirCajas() {
        // SIN del maletin, en orden maletin desc, creado_en desc, id desc: C8, C7, C6, C4, C2
        Page<PdvCaja> p0 = maletin("SIN_DIFERENCIA", 0, 3);
        Page<PdvCaja> p1 = maletin("SIN_DIFERENCIA", 1, 3);
        Page<PdvCaja> p2 = maletin("SIN_DIFERENCIA", 2, 3);

        assertEquals(ids(C8, C7, C6), ids(p0));
        assertEquals(ids(C4, C2), ids(p1), "la ultima pagina, parcial");
        assertEquals(ids(), ids(p2));
        assertEquals(5, p0.getTotalElements());
        assertEquals(5, p1.getTotalElements());
        assertEquals(5, p2.getTotalElements(), "pasada del final, el total sigue");
        assertEquals(2, p0.getTotalPages());
        p0.getContent().forEach(c -> assertEquals("SIN_DIFERENCIA", c.getEstadoDiferenciaConsecutiva()));
    }

    @Test
    void conDiferenciaYFiltrosCombinados() {
        assertEquals(ids(C3), ids(maletin("CON_DIFERENCIA", 0, 10)));
        // por descripcion del maletin, sin fechas (lo que manda el desktop)
        Page<PdvCaja> desc = service.findAllForAnalisisDiferencias(null, null, null, null, "zz609 male", null,
                null, null, null, null, "CON_DIFERENCIA", PageRequest.of(0, 10));
        assertEquals(ids(C3), ids(desc));
        // estado de la caja (enum)
        Page<PdvCaja> estado = service.findAllForAnalisisDiferencias(null, null, PdvCajaEstado.CONCLUIDO, MALETIN, null,
                null, DESDE, HASTA, null, null, "SIN_DIFERENCIA", PageRequest.of(0, 10));
        assertEquals(ids(C4), ids(estado));
        // verificado
        Page<PdvCaja> verif = service.findAllForAnalisisDiferencias(null, null, null, MALETIN, null, null,
                DESDE, HASTA, null, true, "SIN_DIFERENCIA", PageRequest.of(0, 10));
        assertEquals(ids(C2), ids(verif));
        // caja anterior + fechas (camino de la base): las dos que siguen a C4
        Page<PdvCaja> ant = service.findAllForAnalisisDiferencias(null, C4, null, null, null, null,
                DESDE, HASTA, SUC, null, null, PageRequest.of(0, 10));
        assertEquals(ids(C7, C6), ids(ant));
        assertEquals(2, ant.getTotalElements());
    }

    @Test
    void sinDatosIncluyeLaCajaSinMaletinPrimero() {
        Page<PdvCaja> p = service.findAllForAnalisisDiferencias(null, null, null, null, null, null,
                DESDE, HASTA, SUC, null, "SIN_DATOS", PageRequest.of(0, 10));
        assertEquals(ids(C9, C5, C1), ids(p), "maletin nulo primero, como el orden desc de hoy");
    }

    @Test
    void elAtajoPorCajaAnteriorAplicaElEstado() {
        // solo cajaAnteriorId: camino optimizado (primera sucesora de C4 con conteo de apertura: C6)
        assertEquals(ids(C6), ids(service.findAllForAnalisisDiferencias(null, C4, null, null, null, null,
                null, null, null, null, null, PageRequest.of(0, 10))));
        assertEquals(ids(C6), ids(service.findAllForAnalisisDiferencias(null, C4, null, null, null, null,
                null, null, null, null, "SIN_DIFERENCIA", PageRequest.of(0, 10))));
        assertEquals(ids(), ids(service.findAllForAnalisisDiferencias(null, C4, null, null, null, null,
                null, null, null, null, "CON_DIFERENCIA", PageRequest.of(0, 10))));
    }

    @Test
    void sinFiltroDeEstadoSigueIgual() {
        Page<PdvCaja> p = service.findAllForAnalisisDiferencias(null, null, null, MALETIN, null, null,
                DESDE, HASTA, null, null, null, PageRequest.of(0, 20));
        assertEquals(8, p.getTotalElements());
        assertTrue(p.getContent().stream().allMatch(c -> c.getEstadoDiferenciaConsecutiva() != null));
    }

    private Page<PdvCaja> maletin(String difEstado, int pagina, int tam) {
        return service.findAllForAnalisisDiferencias(null, null, null, MALETIN, null, null,
                DESDE, HASTA, null, null, difEstado, PageRequest.of(pagina, tam));
    }

    private Page<PdvCaja> porAnterior(long anteriorId) {
        return service.findAllForAnalisisDiferencias(null, anteriorId, null, MALETIN, null, null,
                DESDE, HASTA, null, null, null, PageRequest.of(0, 10));
    }

    private String estadoSql(long id) {
        for (String e : Arrays.asList("SIN_DATOS", "SIN_DIFERENCIA", "CON_DIFERENCIA")) {
            if (!repository.findAnalisisDiferencias(String.valueOf(SUC), String.valueOf(id), null, null, null, null,
                    null, null, null, null, e, 10, 0).isEmpty()) return e;
        }
        return null;
    }

    private static List<Long> ids(Long... ids) {
        return Arrays.asList(ids);
    }

    private static List<Long> ids(Page<PdvCaja> p) {
        return p.getContent().stream().map(PdvCaja::getId).collect(Collectors.toList());
    }

    /** Un billete: [moneda_billetes_id, cantidad]. */
    private static Object[] m(long billete, Integer cantidad) {
        return new Object[]{billete, cantidad};
    }

    private Long conteo(Object[]... billetes) {
        long id = conteoId;
        conteoId += 2;
        sql("insert into financiero.conteo (id, sucursal_id) values (" + id + ", " + SUC + ")");
        for (Object[] b : billetes) {
            sql("insert into financiero.conteo_moneda (id, sucursal_id, conteo_id, moneda_billetes_id, cantidad) values ("
                    + conteoMonedaId + ", " + SUC + ", " + id + ", " + b[0] + ", " + b[1] + ")");
            conteoMonedaId += 2;
        }
        return id;
    }

    private void caja(long id, Long maletin, String apertura, String cierre, String creado, Long conteoApertura,
                      Long conteoCierre) {
        sql("insert into financiero.pdv_caja (id, sucursal_id, maletin_id, fecha_apertura, fecha_cierre, creado_en, "
                + "conteo_apertura_id, conteo_cierre_id, verificado, activo) values (" + id + ", " + SUC + ", " + maletin
                + ", " + hora(apertura) + ", " + hora(cierre) + ", " + hora(creado) + ", " + conteoApertura + ", "
                + conteoCierre + ", false, false)");
    }

    private static String hora(String hhmm) {
        return hhmm == null ? "null" : "'2001-01-01 " + hhmm + "'";
    }

    private void sql(String s) {
        em.createNativeQuery(s).executeUpdate();
    }
}
