package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.ProductoRechazadoDto;
import com.franco.dev.graphql.operaciones.ProductoRechazadoGraphQL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProductoRechazadoServiceTest {

    @Test
    void fuenteDesconocidaEsTodas() {
        assertNull(ProductoRechazadoService.normalizarFuente(null));
        assertNull(ProductoRechazadoService.normalizarFuente("VENTA"));
        assertEquals("COMPRA", ProductoRechazadoService.normalizarFuente(" compra "));
        assertEquals("TRANSFERENCIA", ProductoRechazadoService.normalizarFuente("TRANSFERENCIA"));
    }

    /**
     * Corre la consulta real contra una base local (por defecto bodega3 en :5551). Nunca apuntar a
     * 172.25.1.200. Se activa con -Dit.rechazados=true; sin la propiedad no hace nada.
     */
    @Test
    @EnabledIfSystemProperty(named = "it.rechazados", matches = "true")
    void consultaRealContraBaseLocal() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                System.getProperty("it.url", "jdbc:postgresql://localhost:5551/bodega3"), "franco", "franco");
        ProductoRechazadoService service = new ProductoRechazadoService(new NamedParameterJdbcTemplate(ds));

        Page<ProductoRechazadoDto> todas = service.buscar(null, null, null, null, null, null, 0, 15);
        assertTrue(todas.getTotalElements() > 0, "bodega3 tiene rechazos");
        assertEquals(15, todas.getContent().size());
        LocalDateTime anterior = null;
        for (ProductoRechazadoDto d : todas.getContent()) {
            assertNotNull(d.getFuente());
            assertNotNull(d.getProductoDescripcion());
            if (anterior != null && d.getFecha() != null) assertFalse(d.getFecha().isAfter(anterior), "orden por fecha desc");
            if (d.getFecha() != null) anterior = d.getFecha();
        }

        Page<ProductoRechazadoDto> compras = service.buscar("COMPRA", null, null, null, null, null, 0, 100);
        assertTrue(compras.getTotalElements() > 0);
        compras.getContent().forEach(d -> assertEquals("COMPRA", d.getFuente()));

        Page<ProductoRechazadoDto> transf = service.buscar("TRANSFERENCIA",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, "coca", 0, 50);
        assertTrue(transf.getTotalElements() > 0);
        transf.getContent().forEach(d -> {
            assertEquals("TRANSFERENCIA", d.getFuente());
            assertTrue(d.getProductoDescripcion().toUpperCase().contains("COCA"));
            assertNotNull(d.getReferenciaId());
        });
        assertEquals(todas.getTotalElements(),
                compras.getTotalElements() + service.buscar("TRANSFERENCIA", null, null, null, null, null, 0, 1).getTotalElements());

        Long ref = transf.getContent().get(0).getReferenciaId();
        Page<ProductoRechazadoDto> porRef = service.buscar(null, null, null, null, ref, null, 0, 500);
        porRef.getContent().forEach(d -> assertEquals(ref, d.getReferenciaId()));
        System.out.printf("total=%d compras=%d transf(sep,coca)=%d ref %d=%d%n", todas.getTotalElements(),
                compras.getTotalElements(), transf.getTotalElements(), ref, porRef.getTotalElements());
    }

    @Test
    void fechaDelResolver() {
        assertNull(callAFecha(null));
        assertNull(callAFecha(""));
        assertEquals(LocalDate.of(2026, 11, 16), callAFecha("2026-11-16"));
        assertEquals(LocalDate.of(2026, 11, 16), callAFecha("2026-11-16 23:59:59"));
    }

    private static LocalDate callAFecha(String s) {
        return ProductoRechazadoGraphQL.aFecha(s);
    }
}
