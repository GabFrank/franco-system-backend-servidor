package com.franco.dev.repository.financiero;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Que el filtro Estado del listado de facturas y la busqueda de facturas de un rango inutilizado
 * lean activo en NULL como activa.
 *
 * Desde fines de 2025 la filial inserto las facturas sin el flag (filial #131) y no se hizo
 * backfill: NULL y true conviven como sinonimos y solo false es cancelada. Antes el filtro comparaba
 * con igual, asi que «Activos» dejaba afuera casi toda la facturacion electronica. Ademas, una
 * electronica cancelada puede quedar con activo en NULL (hay caminos que solo tocan el documento),
 * por eso el filtro mira tambien el estado del documento electronico.
 *
 * Corre contra una base real con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.facturaActivo=true -Dtest=FacturaLegalActivoQueriesIT \
 *       -Dspring.datasource.url=jdbc:postgresql://localhost:5551/una_base
 *
 * Las fechas son de 2099 y los ids de 900 millones para no mezclarse con datos reales. Los ids de
 * documento electronico son impares porque el central rechaza los pares (V226.1). Es transaccional,
 * asi que lo sembrado se revierte al terminar.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@EnabledIfSystemProperty(named = "it.facturaActivo", matches = "true")
class FacturaLegalActivoQueriesIT {

    private static final String DESDE = "2099-03-01T00:00:00";
    private static final String HASTA = "2099-03-02T00:00:00";
    private static final long ID_BASE = 900000000L;
    private static final int NUMERO_BASE = 90000000;

    @Autowired
    private FacturaLegalRepository repository;

    @Autowired
    private EntityManager em;

    private long sucursalId;
    private long timbradoDetalleId;

    @BeforeEach
    void sembrarFacturas() {
        Number ocupados = (Number) em.createNativeQuery("select " +
                        "(select count(*) from financiero.factura_legal where id >= " + ID_BASE + ") + " +
                        "(select count(*) from financiero.documento_electronico where id >= " + ID_BASE + ")")
                .getSingleResult();
        assertEquals(0, ocupados.intValue(), "los ids de prueba tienen que estar libres en la base");

        Object[] td = (Object[]) em.createNativeQuery(
                        "select id, sucursal_id from financiero.timbrado_detalle order by sucursal_id, id limit 1")
                .getSingleResult();
        timbradoDetalleId = ((Number) td[0]).longValue();
        sucursalId = ((Number) td[1]).longValue();

        // Papel
        factura(1, "true", null);
        factura(2, "null", null);
        factura(3, "false", null);
        // Electronicas
        factura(4, "null", "APROBADO");
        factura(5, "null", "CANCELADO");   // cancelada sin tocar la factura
        factura(6, "true", "CANCELADO");   // idem, nacida despues del fix
        factura(7, "true", "APROBADO");
        factura(8, "false", "CANCELADO");

        em.flush();
        em.clear();
    }

    private void factura(int n, String activo, String estadoDe) {
        long id = ID_BASE + n;
        String cdc = estadoDe == null ? "null" : "'IT-FACTURA-ACTIVO-" + n + "'";
        em.createNativeQuery("insert into financiero.factura_legal " +
                        "(id, sucursal_id, timbrado_detalle_id, numero_factura, activo, cdc, creado_en) values (" +
                        id + ", " + sucursalId + ", " + timbradoDetalleId + ", " + (NUMERO_BASE + n) + ", " +
                        activo + ", " + cdc + ", timestamp '2099-03-01 10:00:00')")
                .executeUpdate();
        if (estadoDe != null) {
            em.createNativeQuery("insert into financiero.documento_electronico " +
                            "(id, sucursal_id, factura_legal_id, estado, cdc) values (" +
                            (ID_BASE + 2L * n + 1) + ", " + sucursalId + ", " + id + ", " +
                            "cast('" + estadoDe + "' as financiero.estado_de_enum), " + cdc + ")")
                    .executeUpdate();
        }
    }

    private long contar(Boolean activo, Boolean electronico) {
        return repository.count(FacturaLegalSpecification.withFilters(
                DESDE, HASTA, Collections.singletonList(sucursalId), null, null, electronico, activo, null, null));
    }

    @Test
    void sinFiltroDeEstadoSalenTodas() {
        assertEquals(8, contar(null, null));
    }

    @Test
    void activosIncluyeLasQueEstanEnNull() {
        // 1 (true), 2 (null), 4 (null + aprobado), 7 (true + aprobado)
        assertEquals(4, contar(true, null));
        assertEquals(2, contar(true, false), "papel: true y null");
        assertEquals(2, contar(true, true), "electronicas: solo las de documento no cancelado");
    }

    @Test
    void inactivosIncluyeLasElectronicasCanceladasAunqueActivoNoSeaFalse() {
        // 3 (false), 5 (null + cancelado), 6 (true + cancelado), 8 (false + cancelado)
        assertEquals(4, contar(false, null));
        assertEquals(1, contar(false, false));
        assertEquals(3, contar(false, true));
    }

    @Test
    void activosEInactivosPartenElTotalSinSolaparse() {
        assertEquals(contar(null, null), contar(true, null) + contar(false, null));
    }

    @Test
    void elRangoInutilizadoEncuentraLasFacturasEnNull() {
        // Todas menos las dos con activo en false (3 y 8). Esta consulta no mira el documento: solo
        // vincula al evento los documentos de las facturas del rango.
        assertEquals(6, repository.findByTimbradoDetalleIdAndSucursalIdAndNumeroFacturaBetween(
                timbradoDetalleId, sucursalId, NUMERO_BASE + 1, NUMERO_BASE + 8).size());
    }
}
