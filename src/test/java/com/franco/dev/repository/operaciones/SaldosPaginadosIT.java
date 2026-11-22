package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorre de punta a punta los listados de saldos, de a 500 como el tope de #582, y verifica que
 * no se repita ni se pierda ninguna fila (#674). Antes del desempate, en bodega3 la SUC. CENTRAL
 * daba 2.896 filas y 2.893 productos distintos.
 *
 * Solo lee. Contra una base con datos reales (bodega3 local, nunca produccion):
 *
 *   ./mvnw test -Dit.saldos=true -Dtest=SaldosPaginadosIT \
 *       -Dspring.datasource.url=jdbc:postgresql://localhost:5551/bodega3
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        })
@ActiveProfiles({"dev", "user-dev"})
@EnabledIfSystemProperty(named = "it.saldos", matches = "true")
class SaldosPaginadosIT {

    private static final int SIZE = 500;
    private static final Long CENTRAL = 1L;

    @Autowired
    private MovimientoStockRepository repository;

    @Test
    void positivaDeUnaSucursalSinRepetidosNiFaltantes() {
        recorrer(p -> repository.findProductosConCantidadPositiva(CENTRAL, null, p));
    }

    @Test
    void negativaDeUnaSucursalSinRepetidosNiFaltantes() {
        recorrer(p -> repository.findProductosConCantidadNegativa(CENTRAL, null, p));
    }

    @Test
    void positivaDeTodasLasSucursalesSinRepetidosNiFaltantes() {
        recorrer(p -> repository.findProductosConCantidadPositiva(null, null, p));
    }

    @Test
    void dosRecorridosSeguidosDanLoMismo() {
        List<String> a = recorrer(p -> repository.findProductosConCantidadPositiva(CENTRAL, null, p));
        List<String> b = recorrer(p -> repository.findProductosConCantidadPositiva(CENTRAL, null, p));
        assertEquals(a, b);
    }

    private List<String> recorrer(Function<Pageable, Page<ProductoSaldoDto>> consulta) {
        List<String> filas = new ArrayList<>();
        Page<ProductoSaldoDto> page;
        int n = 0;
        do {
            page = consulta.apply(PageRequest.of(n++, SIZE));
            page.getContent().forEach(s -> filas.add(s.getProductoId() + "@" + s.getSucursalId()));
        } while (page.hasNext());
        Set<String> distintas = new HashSet<>(filas);
        assertTrue(page.getTotalElements() > SIZE, "hacen falta varias paginas para probar algo");
        assertEquals(page.getTotalElements(), filas.size(), "filas recorridas");
        assertEquals(filas.size(), distintas.size(), "filas repetidas entre paginas");
        return filas;
    }
}
