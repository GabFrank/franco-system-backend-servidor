package com.franco.dev.repository.operaciones;

import com.franco.dev.repository.productos.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #457: antes de 2024-10 el aumento se guardaba en negativo aunque el pago lo incluía. Caja y lucro lo
 * suman en valor absoluto y en Gs, igual que el reporte de ventas (#454). Las consultas no se ejecutan
 * en CI; este test fija su texto.
 */
class AumentoValorAbsolutoTest {

    private String sql(Class<?> repositorio, String metodo) {
        Method m = Arrays.stream(repositorio.getMethods())
                .filter(x -> x.getName().equals(metodo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe " + metodo));
        Query query = m.getAnnotation(Query.class);
        assertNotNull(query, metodo + " debe declarar @Query");
        return query.value().replaceAll("\\s+", " ");
    }

    @Test
    void balanceDeCajaSumaElAumentoEnValorAbsolutoYEnGs() {
        String sql = sql(VentaRepository.class, "sumarioVentasPorCajaAndSurusal");
        assertTrue(sql.contains("SUM(CASE WHEN cd.aumento THEN ABS(cd.valor * COALESCE(cd.cambio, 1)) ELSE 0 END) AS totalAumento"),
                "un aumento negativo resta y uno en reales se suma sin convertir");
    }

    @Test
    void lucroPorFuncionarioSumaElAumentoEnValorAbsoluto() {
        String sql = sql(VentaItemRepository.class, "findLucroPorFuncionarioNative");
        assertTrue(sql.contains("SUM(CASE WHEN cd.aumento = true THEN ABS(cd.valor * COALESCE(cd.cambio, 1)) ELSE 0 END) AS aum_total"),
                "un aumento negativo baja el lucro");
    }

    @Test
    void lucroPorProductoSumaElAumentoEnValorAbsoluto() {
        String sql = sql(ProductoRepository.class, "findLucroPorProducto");
        assertTrue(sql.contains("SELECT SUM(ABS(cd.valor * COALESCE(cd.cambio, 1.0))) FROM CobroDetalle cd WHERE cd.cobro = v.cobro AND cd.aumento = true"),
                "un aumento negativo baja el lucro");
    }
}
