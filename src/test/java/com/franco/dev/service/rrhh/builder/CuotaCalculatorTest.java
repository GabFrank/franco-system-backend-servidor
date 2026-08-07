package com.franco.dev.service.rrhh.builder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CuotaCalculatorTest {

    @Test
    void divisionExacta_todasLasCuotasIguales() {
        List<BigDecimal> cuotas = CuotaCalculator.calcularCuotas(new BigDecimal("1200000"), 3);
        assertEquals(3, cuotas.size());
        assertEquals(0, new BigDecimal("400000").compareTo(cuotas.get(0)));
        assertEquals(0, new BigDecimal("400000").compareTo(cuotas.get(1)));
        assertEquals(0, new BigDecimal("400000").compareTo(cuotas.get(2)));
    }

    @Test
    void divisionInexacta_ultimaCuotaAbsorbeRedondeo() {
        // 1.000.000 / 3 = 333.333,33 (base); ultima = 1.000.000 - 666.666,66 = 333.333,34
        List<BigDecimal> cuotas = CuotaCalculator.calcularCuotas(new BigDecimal("1000000"), 3);
        assertEquals(0, new BigDecimal("333333.33").compareTo(cuotas.get(0)));
        assertEquals(0, new BigDecimal("333333.33").compareTo(cuotas.get(1)));
        assertEquals(0, new BigDecimal("333333.34").compareTo(cuotas.get(2)));
    }

    @Test
    void sumaDeCuotas_igualAlTotal() {
        BigDecimal total = new BigDecimal("1000000");
        List<BigDecimal> cuotas = CuotaCalculator.calcularCuotas(total, 7);
        BigDecimal suma = cuotas.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(suma));
    }

    @Test
    void unaSolaCuota_igualAlTotal() {
        List<BigDecimal> cuotas = CuotaCalculator.calcularCuotas(new BigDecimal("777777"), 1);
        assertEquals(1, cuotas.size());
        assertEquals(0, new BigDecimal("777777").compareTo(cuotas.get(0)));
    }

    @Test
    void cantidadCuotasInvalida_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> CuotaCalculator.calcularCuotas(new BigDecimal("100000"), 0));
    }

    @Test
    void totalNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> CuotaCalculator.calcularCuotas(null, 3));
    }
}
