package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import org.hibernate.jpa.TypedParameterValue;
import org.hibernate.type.LongType;
import org.hibernate.type.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Que la lista de marcaciones salga de la llegada mas reciente a la mas vieja.
 *
 * Antes ordenaba por {@code j.id DESC}, pero el id de la jornada es por sucursal: dos llegadas del
 * mismo dia en sucursales distintas quedaban ordenadas por el contador de cada una, no por la hora.
 * Cada caso siembra los ids en contra del orden esperado, asi que con {@code id DESC} falla.
 *
 * Corre contra una base real con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.jornadaOrden=true -Dtest=JornadaOrdenQueriesIT \
 *       -Dspring.datasource.url=jdbc:postgresql://localhost:5551/una_base
 *
 * Las fechas son de 2099 para no mezclarse con jornadas reales: {@code findByFechaRange} no filtra
 * por usuario. Los ids de marcacion son impares porque el central rechaza los pares (V223.1). Es
 * transaccional, asi que lo sembrado se revierte al terminar.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@EnabledIfSystemProperty(named = "it.jornadaOrden", matches = "true")
class JornadaOrdenQueriesIT {

    private static final String DIA_VIEJO = "2099-01-01";
    private static final String DIA_NUEVO = "2099-01-02";

    @Autowired
    private JornadaRepository repository;

    @Autowired
    private EntityManager em;

    private Long usuarioId;
    private Long otroUsuarioId;
    private List<Long> sucursales;
    private long proximaMarcacionId = 900000001L;

    @BeforeEach
    void sembrarJornadas() {
        Number enRango = (Number) em.createNativeQuery("select count(*) from administrativo.jornada " +
                        "where fecha between cast(:ini as date) and cast(:fin as date)")
                .setParameter("ini", DIA_VIEJO).setParameter("fin", DIA_NUEVO)
                .getSingleResult();
        assertEquals(0, enRango.intValue(), "el rango de prueba tiene que estar vacio en la base");

        List<?> usuarios = em.createNativeQuery("select id from personas.usuario order by id limit 2")
                .getResultList();
        usuarioId = ((Number) usuarios.get(0)).longValue();
        otroUsuarioId = ((Number) usuarios.get(1)).longValue();
        sucursales = ((List<?>) em.createNativeQuery("select id from empresarial.sucursal order by id limit 3")
                .getResultList()).stream().map(n -> ((Number) n).longValue()).collect(Collectors.toList());

        long s0 = sucursales.get(0), s1 = sucursales.get(1), s2 = sucursales.get(2);

        // Dia nuevo, tres sucursales: el id crece al reves de la hora de llegada.
        jornada(3, s0, usuarioId, DIA_NUEVO, entrada(s0, usuarioId, DIA_NUEVO + " 08:00", null));
        jornada(2, s1, usuarioId, DIA_NUEVO, entrada(s1, usuarioId, DIA_NUEVO + " 09:00", null));
        jornada(1, s2, usuarioId, DIA_NUEVO, entrada(s2, usuarioId, DIA_NUEVO + " 10:00", null));
        // Entrada sin fecha_entrada: cuenta la fecha_salida, como en el reporte impreso. Id mas bajo.
        jornada(0, s1, usuarioId, DIA_NUEVO, entrada(s1, usuarioId, null, DIA_NUEVO + " 11:00"));
        // Sin marcacion de entrada, con el id mas alto del dia: va ultima de su dia.
        jornada(9, s0, usuarioId, DIA_NUEVO, null);

        // Dia viejo con ids mas altos que todo el dia nuevo.
        jornada(10, s0, usuarioId, DIA_VIEJO, entrada(s0, usuarioId, DIA_VIEJO + " 07:00", null));
        // Otro usuario, dia viejo, llegada mas temprana.
        jornada(11, s2, otroUsuarioId, DIA_VIEJO, entrada(s2, otroUsuarioId, DIA_VIEJO + " 06:00", null));

        em.flush();
        em.clear();
    }

    /** Inserta una marcacion de entrada y devuelve su clave {id, sucursalId}. */
    private long[] entrada(long sucursalId, long usuario, String fechaEntrada, String fechaSalida) {
        long id = proximaMarcacionId;
        proximaMarcacionId += 2;
        em.createNativeQuery("insert into administrativo.marcacion " +
                        "(id, sucursal_id, usuario_id, tipo_marcacion, sucursal_entrada_id, fecha_entrada, fecha_salida) " +
                        "values (:id, :suc, :usu, cast('ENTRADA' as administrativo.tipo_marcacion), :suc, " +
                        "cast(:fe as timestamptz), cast(:fs as timestamp))")
                .setParameter("id", id)
                .setParameter("suc", sucursalId)
                .setParameter("usu", usuario)
                // Tipados: un null suelto Hibernate lo manda como bytea y Postgres no lo castea.
                .setParameter("fe", new TypedParameterValue(StringType.INSTANCE, fechaEntrada))
                .setParameter("fs", new TypedParameterValue(StringType.INSTANCE, fechaSalida))
                .executeUpdate();
        return new long[]{id, sucursalId};
    }

    private void jornada(long idRelativo, long sucursalId, long usuario, String fecha, long[] entrada) {
        em.createNativeQuery("insert into administrativo.jornada " +
                        "(id, sucursal_id, usuario_id, fecha, entrada_id, entrada_sucursal_id) " +
                        "values (:id, :suc, :usu, cast(:fecha as date), :ent, :entSuc)")
                .setParameter("id", 900000000L + idRelativo)
                .setParameter("suc", sucursalId)
                .setParameter("usu", usuario)
                .setParameter("fecha", fecha)
                .setParameter("ent", new TypedParameterValue(LongType.INSTANCE, entrada == null ? null : entrada[0]))
                .setParameter("entSuc", new TypedParameterValue(LongType.INSTANCE, entrada == null ? null : entrada[1]))
                .executeUpdate();
    }

    private static List<Long> idsRelativos(List<Jornada> jornadas) {
        return jornadas.stream().map(j -> j.getId() - 900000000L).collect(Collectors.toList());
    }

    @Test
    void porRangoSaleDeLaLlegadaMasRecienteALaMasVieja() {
        List<Jornada> jornadas = repository.findByFechaRange(DIA_VIEJO, DIA_NUEVO);

        assertEquals(Arrays.asList(0L, 1L, 2L, 3L, 9L, 10L, 11L), idsRelativos(jornadas));
    }

    @Test
    void porUsuarioOrdenaIgualYNoTraeAOtros() {
        List<Jornada> jornadas = repository.findByUsuarioIdAndFechaRange(usuarioId, DIA_VIEJO, DIA_NUEVO);

        assertEquals(Arrays.asList(0L, 1L, 2L, 3L, 9L, 10L), idsRelativos(jornadas));
    }
}
