package com.franco.dev.repository.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.dto.JornadaHistorial;
import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.service.administrativo.JornadaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Que el historial paginado devuelva lo mismo que antes armaba el desktop: el rango entero de
 * {@code findByFechaRange}, filtrado por turno y sucursal en memoria, cortado en paginas y sumado.
 *
 * No siembra nada: lee los ultimos 31 dias con jornadas de la base y compara las dos formas. Corre contra una
 * base real con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.jornadaHistorial=true -Dtest=JornadaHistorialQueriesIT
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional(readOnly = true)
@EnabledIfSystemProperty(named = "it.jornadaHistorial", matches = "true")
class JornadaHistorialQueriesIT {

    private static final int TAMANO = 7;

    @Autowired
    private JornadaRepository repository;

    @Autowired
    private JornadaService service;

    @Autowired
    private EntityManager em;

    private String inicio;
    private String fin;
    private List<Jornada> rango;

    @BeforeEach
    void leerRango() {
        Object ultima = em.createNativeQuery("select cast(max(fecha) as varchar) from administrativo.jornada")
                .getSingleResult();
        LocalDate hasta = LocalDate.parse(String.valueOf(ultima).substring(0, 10));
        fin = hasta.toString();
        inicio = hasta.minusDays(30).toString();
        rango = repository.findByFechaRange(inicio, fin);
        assertFalse(rango.isEmpty(), "la base tiene que tener jornadas para comparar");
    }

    @Test
    void sinFiltrosDevuelveElRangoEnteroEnElMismoOrden() {
        comparar(null, null, null, null, j -> true);
    }

    @Test
    void filtraPorUsuario() {
        Long usuarioId = rango.get(0).getUsuario().getId();
        comparar(usuarioId, null, null, null, j -> j.getUsuario().getId().equals(usuarioId));
    }

    @Test
    void filtraPorTurno() {
        for (Turno turno : Turno.values()) {
            comparar(null, turno, null, null, j -> j.getTurno() == turno);
        }
    }

    @Test
    void filtraPorSucursalDeEntradaYDeSalida() {
        Long entrada = rango.stream().filter(j -> j.getMarcacionEntrada() != null
                        && j.getMarcacionEntrada().getSucursalEntrada() != null)
                .map(j -> j.getMarcacionEntrada().getSucursalEntrada().getId()).findFirst().orElse(-1L);
        comparar(null, null, entrada, null, j -> j.getMarcacionEntrada() != null
                && j.getMarcacionEntrada().getSucursalEntrada() != null
                && j.getMarcacionEntrada().getSucursalEntrada().getId().equals(entrada));

        Long salida = rango.stream().filter(j -> j.getMarcacionSalida() != null
                        && j.getMarcacionSalida().getSucursalSalida() != null)
                .map(j -> j.getMarcacionSalida().getSucursalSalida().getId()).findFirst().orElse(-1L);
        comparar(null, null, null, salida, j -> j.getMarcacionSalida() != null
                && j.getMarcacionSalida().getSucursalSalida() != null
                && j.getMarcacionSalida().getSucursalSalida().getId().equals(salida));
    }

    private void comparar(Long usuarioId, Turno turno, Long sucursalEntradaId, Long sucursalSalidaId,
            Predicate<Jornada> filtro) {
        List<Jornada> esperadas = rango.stream().filter(filtro).collect(Collectors.toList());

        List<String> paginadas = new ArrayList<>();
        JornadaHistorial historial = null;
        int pagina = 0;
        do {
            historial = service.findHistorial(usuarioId, inicio, fin, turno, sucursalEntradaId, sucursalSalidaId,
                    pagina++, TAMANO);
            historial.getPagina().getContent().forEach(j -> paginadas.add(clave(j)));
        } while (historial.getPagina().hasNext());

        assertEquals(esperadas.stream().map(JornadaHistorialQueriesIT::clave).collect(Collectors.toList()), paginadas);
        assertEquals(esperadas.size(), historial.getPagina().getTotalElements());
        assertEquals((int) esperadas.stream().map(Jornada::getFecha).distinct().count(),
                historial.getResumen().getDiasTrabajados());
        assertEquals(suma(esperadas, Jornada::getMinutosTrabajados), historial.getResumen().getMinutosTrabajados());
        assertEquals(suma(esperadas, Jornada::getMinutosExtras), historial.getResumen().getMinutosExtras());
        assertEquals(suma(esperadas, Jornada::getMinutosLlegadaTardia),
                historial.getResumen().getMinutosLlegadaTardia());
    }

    private static int suma(List<Jornada> jornadas, java.util.function.Function<Jornada, Long> campo) {
        return (int) jornadas.stream().map(campo).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private static String clave(Jornada j) {
        return j.getId() + "-" + j.getSucursalId();
    }
}
