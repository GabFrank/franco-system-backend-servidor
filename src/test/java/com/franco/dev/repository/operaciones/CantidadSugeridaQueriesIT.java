package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.dto.ComprasPorSucursalDto;
import com.franco.dev.domain.operaciones.dto.VentasPorSucursalDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que las dos consultas agregadas de la cantidad sugerida realmente corran.
 *
 * Los tests unitarios del service mockean el repositorio, asi que no tocan el JPQL. Acá está lo
 * unico que esos no pueden cubrir, y que ademas no falla en el build sino recien cuando el JAR
 * arranca contra una base:
 *
 * - el {@code cast(ms.tipoMovimiento as text)} sobre el enum de Postgres,
 * - el {@code COUNT(ms.creadoEn)} en una entidad con clave compuesta ({@code @IdClass}), que con
 *   {@code count(ms)} generaria un count de dos columnas que Postgres rechaza,
 * - la proyeccion a DTO con {@code new ...(...)}, donde el tipo que Hibernate infiere para cada
 *   agregado tiene que coincidir con el constructor.
 *
 * Corre contra una base real con el esquema aplicado, y se pide a mano:
 *
 *   ./mvnw test -Dit.cantidadSugerida=true -Dtest=CantidadSugeridaQueriesIT \
 *       -Dspring.datasource.url=jdbc:postgresql://localhost:5551/una_base
 *
 * Flyway queda apagado a proposito: el test necesita el esquema, no aplicarlo. Es transaccional,
 * asi que las filas que inserta se revierten al terminar. Y arranca sin web ({@code NONE}): con el
 * contexto web, {@code GraphQLWebsocketAutoConfiguration} pide un {@code ServerContainer} que en un
 * test no existe, y el contexto ni levanta.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@EnabledIfSystemProperty(named = "it.cantidadSugerida", matches = "true")
class CantidadSugeridaQueriesIT {

    private static final long PRODUCTO_ID = 999111222L;
    private static final LocalDateTime INICIO = LocalDateTime.of(2025, 9, 1, 0, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2025, 9, 30, 23, 59, 59);
    private static final List<Long> SUCURSALES = Arrays.asList(1L, 2L);

    @Autowired
    private MovimientoStockRepository repository;

    @Autowired
    private EntityManager em;

    private long proximoId = 900000000L;

    @BeforeEach
    void sembrarMovimientos() {
        em.createNativeQuery("insert into productos.producto (id) values (:id)")
                .setParameter("id", PRODUCTO_ID).executeUpdate();

        // Sucursal 1: dos ventas vivas y una anulada, mas dos compras.
        mov(1L, "VENTA", -30, "2025-09-03 10:00", true);
        mov(1L, "VENTA", -20, "2025-09-10 10:00", true);
        mov(1L, "VENTA", -99, "2025-09-11 10:00", false);
        mov(1L, "COMPRA", 100, "2025-09-02 08:00", true);
        mov(1L, "COMPRA", 100, "2025-09-22 08:00", true);

        // Sucursal 2: una transferencia de entrada, una de salida (no cuenta) y una fuera del rango.
        mov(2L, "TRANSFERENCIA", 40, "2025-09-05 09:00", true);
        mov(2L, "TRANSFERENCIA", -40, "2025-09-06 09:00", true);
        mov(2L, "COMPRA", 10, "2025-08-01 09:00", true);

        em.flush();
    }

    private void mov(Long sucursalId, String tipo, double cantidad, String creadoEn, boolean estado) {
        em.createNativeQuery("insert into operaciones.movimiento_stock " +
                        "(id, producto_id, tipo_movimiento, referencia, cantidad, creado_en, estado, sucursal_id) " +
                        "values (:id, :pro, cast(:tipo as operaciones.tipo_movimiento), 0, :cant, cast(:fecha as timestamptz), :estado, :suc)")
                .setParameter("id", proximoId++)
                .setParameter("pro", PRODUCTO_ID)
                .setParameter("tipo", tipo)
                .setParameter("cant", cantidad)
                .setParameter("fecha", creadoEn)
                .setParameter("estado", estado)
                .setParameter("suc", sucursalId)
                .executeUpdate();
    }

    @Test
    void sumaLasVentasVivasEnValorAbsolutoYDescartaLasAnuladas() {
        List<VentasPorSucursalDto> filas = repository.ventasPorSucursal(PRODUCTO_ID, INICIO, FIN, SUCURSALES);

        assertEquals(1, filas.size(), "solo la sucursal 1 tuvo ventas");
        assertEquals(1L, filas.get(0).getSucursalId());
        // 30 + 20 en positivo. La venta anulada de 99 no entra.
        assertEquals(50.0, filas.get(0).getTotalVentas(), 0.0001);
    }

    @Test
    void cuentaLasComprasYLasTransferenciasDeEntradaConSusFechas() {
        List<ComprasPorSucursalDto> filas = repository.comprasPorSucursal(PRODUCTO_ID, INICIO, FIN, SUCURSALES);

        ComprasPorSucursalDto suc1 = filas.stream().filter(f -> f.getSucursalId() == 1L)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2L, suc1.getCantidadCompras());
        assertEquals(LocalDateTime.of(2025, 9, 2, 8, 0), suc1.getPrimeraCompra());
        assertEquals(LocalDateTime.of(2025, 9, 22, 8, 0), suc1.getUltimaCompra());

        // La transferencia de salida (-40) no es una entrada; la compra de agosto queda fuera del rango.
        ComprasPorSucursalDto suc2 = filas.stream().filter(f -> f.getSucursalId() == 2L)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(1L, suc2.getCantidadCompras());
        assertEquals(LocalDateTime.of(2025, 9, 5, 9, 0), suc2.getUltimaCompra());
    }

    @Test
    void sucursalFueraDeLaListaNoVuelve() {
        List<VentasPorSucursalDto> filas = repository.ventasPorSucursal(
                PRODUCTO_ID, INICIO, FIN, Collections.singletonList(2L));

        assertTrue(filas.isEmpty());
    }
}
