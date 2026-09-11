package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT del filtro por ciudad de la nomina del mes, contra la DB dev real.
 *
 * Aprueba temporalmente las liquidaciones del periodo (el reporte solo toma
 * APROBADA/PAGADA) y genera los tres cortes: todas, una ciudad y sin ciudad.
 * @Transactional -> rollback: las liquidaciones vuelven a BORRADOR al terminar.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.nomina=true.
 * Con -Dnomina.out=<dir> ademas escribe los PDFs para mirarlos.
 *
 * Correr:  ./mvnw -Dit.nomina=true -Dtest=NominaCiudadIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.nomina", matches = "true")
public class NominaCiudadIT {

    private static final String PERIODO = System.getProperty("nomina.periodo", "2026-08");

    @Autowired
    private ReporteRrhhService service;

    @PersistenceContext
    private EntityManager em;

    @Test
    void generaLosTresCortes() throws Exception {
        List<LiquidacionSueldo> liqs = em.createQuery(
                "select l from LiquidacionSueldo l where l.periodo = :p", LiquidacionSueldo.class)
                .setParameter("p", PERIODO).getResultList();
        assumeTrue(!liqs.isEmpty(), "DB dev sin liquidaciones en " + PERIODO);
        liqs.forEach(l -> l.setEstado(LiquidacionSueldoEstado.APROBADA));
        em.flush();

        Long ciudadId = em.createQuery(
                "select s.ciudad.id from Funcionario f join f.sucursal s where s.ciudad is not null", Long.class)
                .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        assumeTrue(ciudadId != null, "DB dev sin sucursales con ciudad");

        byte[] todas = pdf(service.nominaMesBase64(PERIODO, null, null));
        byte[] unaCiudad = pdf(service.nominaMesBase64(PERIODO, ciudadId, null));
        byte[] sinCiudad = pdf(service.nominaMesBase64(PERIODO, null, true));

        assertTrue(todas.length > 0 && unaCiudad.length > 0 && sinCiudad.length > 0);
        // Un corte por ciudad no puede pesar lo mismo que la nomina entera: si pesa igual,
        // el filtro no se aplico.
        assertNotEquals(todas.length, unaCiudad.length, "el filtro por ciudad no filtro nada");
        assertNotEquals(todas.length, sinCiudad.length, "el filtro sin-ciudad no filtro nada");

        String out = System.getProperty("nomina.out");
        if (out != null) {
            Files.write(Paths.get(out, "nomina-todas.pdf"), todas);
            Files.write(Paths.get(out, "nomina-ciudad-" + ciudadId + ".pdf"), unaCiudad);
            Files.write(Paths.get(out, "nomina-sin-ciudad.pdf"), sinCiudad);
        }
    }

    private byte[] pdf(String base64) {
        assertNotNull(base64, "el servicio devolvio null");
        return Base64.getDecoder().decode(base64);
    }
}
