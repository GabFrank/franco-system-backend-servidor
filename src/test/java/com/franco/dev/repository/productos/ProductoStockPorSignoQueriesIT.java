package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El filtro de stock POSITIVO/NEGATIVO de la lista de productos, en sus dos queries
 * ({@code searchWithFilters} sin texto y {@code searchWithFiltersByIds}, el camino de la búsqueda
 * por texto). Sin sucursal elegida, un producto entra si alguna sucursal tiene stock de ese signo
 * —no la suma de todas— y COMPRAS no cuenta: no vende, y su negativo tapaba al resto.
 *
 * Corre contra una base real con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.stockPorSigno=true -Dtest=ProductoStockPorSignoQueriesIT
 *
 * Igual que {@code CantidadSugeridaQueriesIT}: Flyway apagado, transaccional (las filas sembradas
 * se revierten) y sin contexto web.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@EnabledIfSystemProperty(named = "it.stockPorSigno", matches = "true")
class ProductoStockPorSignoQueriesIT {

    /** +10 en la sucursal 1 y −20 en la 3: la suma da −10. */
    private static final long MIXTO = 999222001L;
    /** Movimientos que se anulan: cero en la sucursal 1. */
    private static final long CERO = 999222002L;
    /** Positivo solo en COMPRAS, negativo en la sucursal 1. */
    private static final long SOLO_COMPRAS = 999222003L;
    private static final List<Long> SEMBRADOS = Arrays.asList(MIXTO, CERO, SOLO_COMPRAS);

    @Autowired
    private ProductoRepository repository;

    @Autowired
    private EntityManager em;

    /** Impar: el central no inserta ids pares en movimiento_stock (los genera el filial). */
    private long proximoId = 900100001L;
    private Long compras;

    @BeforeEach
    void sembrar() {
        compras = ((Number) em.createNativeQuery(
                "select id from empresarial.sucursal where nombre = 'COMPRAS'").getSingleResult()).longValue();
        for (Long id : SEMBRADOS) {
            em.createNativeQuery("insert into productos.producto (id) values (:id)")
                    .setParameter("id", id).executeUpdate();
        }
        mov(MIXTO, 1L, 10);
        mov(MIXTO, 3L, -20);
        mov(CERO, 1L, 5);
        mov(CERO, 1L, -5);
        mov(SOLO_COMPRAS, compras, 50);
        mov(SOLO_COMPRAS, 1L, -1);
        em.flush();
    }

    private void mov(long productoId, Long sucursalId, double cantidad) {
        em.createNativeQuery("insert into operaciones.movimiento_stock " +
                        "(id, producto_id, tipo_movimiento, referencia, cantidad, creado_en, estado, sucursal_id) " +
                        "values (:id, :pro, cast('AJUSTE' as operaciones.tipo_movimiento), 0, :cant, now(), true, :suc)")
                .setParameter("id", proximoId += 2)
                .setParameter("pro", productoId)
                .setParameter("cant", cantidad)
                .setParameter("suc", sucursalId)
                .executeUpdate();
    }

    /** Los sembrados que trae cada query; las dos tienen que coincidir. */
    private Set<Long> buscan(String stockFiltro, Long sucursalId) {
        Set<Long> sinTexto = repository.searchWithFilters(null, SEMBRADOS, true, null, null, null, null, null,
                        null, null, stockFiltro, sucursalId, Pageable.unpaged())
                .getContent().stream().map(Producto::getId).collect(Collectors.toSet());
        Set<Long> porIds = repository.searchWithFiltersByIds(SEMBRADOS, null, null, null, null, null, null, null,
                        stockFiltro, sucursalId)
                .stream().map(Producto::getId).collect(Collectors.toSet());
        assertEquals(sinTexto, porIds, "las dos queries filtran igual");
        return porIds;
    }

    private static Set<Long> ids(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    void positivoSinSucursalTraeAlQueTieneAlgunaSucursalPositivaAunqueLaSumaDeNegativa() {
        // SOLO_COMPRAS no entra: su único positivo es COMPRAS.
        assertEquals(ids(MIXTO), buscan("positivo", null));
    }

    @Test
    void negativoSinSucursalTraeAlQueTieneAlgunaSucursalNegativa() {
        assertEquals(ids(MIXTO, SOLO_COMPRAS), buscan("negativo", null));
    }

    @Test
    void conSucursalElegidaMiraSoloEsaSucursal() {
        assertEquals(ids(MIXTO), buscan("positivo", 1L));
        assertEquals(ids(SOLO_COMPRAS), buscan("negativo", 1L));
        assertEquals(ids(MIXTO), buscan("negativo", 3L));
    }

    @Test
    void conComprasElegidaSiCuentaComprasYElCeroNoEntraEnNinguno() {
        assertEquals(ids(SOLO_COMPRAS), buscan("positivo", compras));
        assertEquals(ids(), buscan("negativo", compras));
    }

    @Test
    void todosNoFiltraPorStock() {
        assertEquals(ids(MIXTO, CERO, SOLO_COMPRAS), buscan("todos", null));
    }
}
