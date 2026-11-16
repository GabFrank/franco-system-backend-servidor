package com.franco.dev.repository.financiero;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La consulta del aviso «caja ya procesada» del diálogo de ingreso a caja mayor, contra una base real: es
 * un {@code select new} con joins ad hoc que solo se valida al ejecutarse.
 *
 * Corre contra una base con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.retiroIngresados=true -Dtest=RetiroIngresadosQueriesIT \
 *       -Dspring.datasource.url=jdbc:postgresql://localhost:5551/una_base
 *
 * Ids de 900 millones para no mezclarse con datos reales; es transaccional y lo sembrado se revierte.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@EnabledIfSystemProperty(named = "it.retiroIngresados", matches = "true")
class RetiroIngresadosQueriesIT {

    private static final long ID = 900000000L;
    private static final long CAJA_PDV = 900000001L;
    private static final PageRequest TOPE = PageRequest.of(0, 50);

    @Autowired
    private RetiroRepository repository;

    @Autowired
    private EntityManager em;

    private long suc1;
    private long suc2;
    private long cajaMayor;
    private String cajaMayorNombre;
    private long usuario;
    private String usuarioNombre;

    @BeforeEach
    void sembrar() {
        Number ocupados = (Number) em.createNativeQuery("select " +
                "(select count(*) from financiero.retiro where id >= " + ID + " or caja_salida_id = " + CAJA_PDV + ") + " +
                "(select count(*) from financiero.movimiento_caja_virtual where id >= " + ID + ")").getSingleResult();
        assertEquals(0, ocupados.intValue(), "los ids de prueba tienen que estar libres en la base");

        List<?> sucs = em.createNativeQuery("select id from empresarial.sucursal where id > 0 order by id limit 2")
                .getResultList();
        suc1 = ((Number) sucs.get(0)).longValue();
        suc2 = ((Number) sucs.get(1)).longValue();
        Object[] cv = (Object[]) em.createNativeQuery("select id, nombre from financiero.caja_virtual order by id limit 1")
                .getSingleResult();
        cajaMayor = ((Number) cv[0]).longValue();
        cajaMayorNombre = (String) cv[1];
        Object[] u = (Object[]) em.createNativeQuery("select u.id, p.nombre from personas.usuario u " +
                "join personas.persona p on p.id = u.persona_id where p.nombre is not null order by u.id limit 1")
                .getSingleResult();
        usuario = ((Number) u[0]).longValue();
        usuarioNombre = (String) u[1];

        // 1: ingresado por un usuario, con dos monedas (dos movimientos, el marcador apunta a uno).
        movimiento(ID + 1, usuario);
        movimiento(ID + 2, usuario);
        retiro(ID + 1, suc1, cajaMayor, ID + 2);
        // 2: ingresado por el poller (movimiento sin usuario).
        movimiento(ID + 3, null);
        retiro(ID + 2, suc1, cajaMayor, ID + 3);
        // 3: verificado sin montos: marcador -1 con caja mayor.
        retiro(ID + 3, suc1, cajaMayor, -1L);
        // 4: limpieza histórica: marcador -1 sin caja mayor.
        retiro(ID + 4, suc1, null, -1L);
        // 5: flotante, no es un procesado.
        retiro(ID + 5, suc1, null, null);
        // 6: la misma caja del PDV en otra sucursal.
        movimiento(ID + 4, usuario);
        retiro(ID + 6, suc2, cajaMayor, ID + 4);
        // 7 y 8: marcados con -1 pero cancelado / todavía abierto en el PDV: no son procesados.
        retiro(ID + 7, suc1, null, -1L);
        retiro(ID + 8, suc1, null, -1L);
        em.createNativeQuery("update financiero.retiro set estado = 'CANCELADO' where id = ?1 and sucursal_id = ?2")
                .setParameter(1, ID + 7).setParameter(2, suc1).executeUpdate();
        em.createNativeQuery("update financiero.retiro set estado = 'EN_PROCESO' where id = ?1 and sucursal_id = ?2")
                .setParameter(1, ID + 8).setParameter(2, suc1).executeUpdate();
        em.flush();
    }

    private void movimiento(long id, Long usuarioId) {
        em.createNativeQuery("insert into financiero.movimiento_caja_virtual " +
                        "(id, caja_virtual_id, tipo_movimiento, cantidad, usuario_id) " +
                        "values (?1, ?2, 'INGRESO', 1000, " + (usuarioId == null ? "null" : usuarioId) + ")")
                .setParameter(1, id).setParameter(2, cajaMayor).executeUpdate();
    }

    private void retiro(long id, long suc, Long caja, Long marcador) {
        em.createNativeQuery("insert into financiero.retiro " +
                        "(id, sucursal_id, caja_salida_id, caja_virtual_id, movimiento_caja_virtual_id, creado_en) " +
                        "values (?1, ?2, ?3, " + (caja == null ? "null" : caja) + ", " +
                        (marcador == null ? "null" : marcador) + ", now() - (?1 - " + ID + ") * interval '1 minute')")
                .setParameter(1, id).setParameter(2, suc).setParameter(3, CAJA_PDV).executeUpdate();
    }

    private static Map<Long, RetiroIngresadoDto> porRetiro(List<RetiroIngresadoDto> l) {
        return l.stream().collect(Collectors.toMap(RetiroIngresadoDto::getRetiroId, Function.identity()));
    }

    @Test
    void trae_los_procesados_de_la_caja_en_la_sucursal_una_fila_por_retiro() {
        List<RetiroIngresadoDto> l = repository.findIngresadosDeCaja(CAJA_PDV, suc1, TOPE);

        assertEquals(Arrays.asList(ID + 1, ID + 2, ID + 3, ID + 4),
                l.stream().map(RetiroIngresadoDto::getRetiroId).collect(Collectors.toList()),
                "sin el flotante, sin la otra sucursal, sin repetir el de dos monedas, del más nuevo al más viejo");
        Map<Long, RetiroIngresadoDto> m = porRetiro(l);

        RetiroIngresadoDto conUsuario = m.get(ID + 1);
        assertEquals(cajaMayorNombre, conUsuario.getCajaVirtualNombre());
        assertEquals(usuarioNombre, conUsuario.getUsuario());
        assertEquals(Long.valueOf(ID + 2), conUsuario.getMovimientoId());
        assertNotNull(conUsuario.getIngresadoEn());
        assertNotNull(conUsuario.getSucursalNombre());

        RetiroIngresadoDto poller = m.get(ID + 2);
        assertNotNull(poller.getMovimientoId());
        assertNull(poller.getUsuario());

        RetiroIngresadoDto sinMontos = m.get(ID + 3);
        assertNull(sinMontos.getMovimientoId());
        assertEquals(cajaMayorNombre, sinMontos.getCajaVirtualNombre());

        RetiroIngresadoDto limpieza = m.get(ID + 4);
        assertNull(limpieza.getMovimientoId());
        assertNull(limpieza.getCajaVirtualId());
        assertNull(limpieza.getCajaVirtualNombre());
    }

    @Test
    void sin_sucursal_trae_la_caja_de_todas_las_sucursales() {
        assertEquals(5, repository.findIngresadosDeCaja(CAJA_PDV, null, TOPE).size());
    }

    @Test
    void la_version_por_sucursales_filtra_por_la_lista() {
        List<RetiroIngresadoDto> l = repository.findIngresadosDeCajaEnSucursales(CAJA_PDV, Arrays.asList(suc2), TOPE);
        assertEquals(1, l.size());
        assertEquals(Long.valueOf(ID + 6), l.get(0).getRetiroId());
    }
}
