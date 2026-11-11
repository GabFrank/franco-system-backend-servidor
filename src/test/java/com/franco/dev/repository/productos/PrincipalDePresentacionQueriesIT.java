package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #605: el precio y el código principal de una presentación llegan con sus propias columnas
 * (id, activo, principal), no con las de la presentación del join.
 *
 * Corre contra una base real con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.principalDePresentacion=true -Dtest=PrincipalDePresentacionQueriesIT
 *
 * Igual que {@code ProductoStockPorSignoQueriesIT}: Flyway apagado, transaccional (las filas
 * sembradas se revierten), sin contexto web y con la réplica apagada.
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
@EnabledIfSystemProperty(named = "it.principalDePresentacion", matches = "true")
class PrincipalDePresentacionQueriesIT {

    private static final long PRODUCTO = 999605007L;
    /** Inactiva y no principal: lo contrario del precio y el código, para que se note de dónde sale cada columna. */
    private static final long PRESENTACION = 999605001L;
    private static final long PRECIO_PRINCIPAL = 999605003L;
    private static final long PRECIO_OTRO = 999605009L;
    private static final long CODIGO_PRINCIPAL = 999605005L;

    @Autowired
    private PrecioPorSucursalRepository precioRepository;

    @Autowired
    private CodigoRepository codigoRepository;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void sembrar() {
        sql("insert into productos.producto (id) values (" + PRODUCTO + ")");
        sql("insert into productos.presentacion (id, producto_id, cantidad, activo, principal) values ("
                + PRESENTACION + ", " + PRODUCTO + ", 1, false, false)");
        sql("insert into productos.precio_por_sucursal (id, presentacion_id, precio, activo, principal) values ("
                + PRECIO_OTRO + ", " + PRESENTACION + ", 9000, true, false)");
        sql("insert into productos.precio_por_sucursal (id, presentacion_id, precio, activo, principal) values ("
                + PRECIO_PRINCIPAL + ", " + PRESENTACION + ", 12500, true, true)");
        sql("insert into productos.codigo (id, presentacion_id, codigo, activo, principal) values ("
                + CODIGO_PRINCIPAL + ", " + PRESENTACION + ", '999605', true, true)");
        em.flush();
        em.clear();
    }

    @Test
    void elPrecioPrincipalTraeSuPropioIdYEstado() {
        PrecioPorSucursal p = precioRepository.findPrincipalByPresentacionId(PRESENTACION);

        assertEquals(PRECIO_PRINCIPAL, p.getId());
        assertEquals(12500d, p.getPrecio());
        assertTrue(p.getActivo());
        assertTrue(p.getPrincipal());
        assertEquals(PRESENTACION, p.getPresentacion().getId());
    }

    @Test
    void elCodigoPrincipalTraeSuPropioIdYEstado() {
        Codigo c = codigoRepository.findPrincipalByPresentacionId(PRESENTACION);

        assertEquals(CODIGO_PRINCIPAL, c.getId());
        assertEquals("999605", c.getCodigo());
        assertTrue(c.getActivo());
        assertTrue(c.getPrincipal());
    }

    private void sql(String s) {
        em.createNativeQuery(s).executeUpdate();
    }
}
