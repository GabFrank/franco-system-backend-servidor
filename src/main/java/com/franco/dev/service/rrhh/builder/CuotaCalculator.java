package com.franco.dev.service.rrhh.builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculo puro del plan de cuotas de un prestamo.
 * cuotaBase = total / n (2 decimales HALF_UP); la ultima cuota absorbe el
 * redondeo para que la suma sea exactamente el total.
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 */
public final class CuotaCalculator {

    private CuotaCalculator() {
    }

    /**
     * Devuelve los montos de las n cuotas. Las primeras n-1 valen cuotaBase;
     * la ultima es total - suma(anteriores), garantizando que el total cierre.
     * @throws IllegalArgumentException si n < 1 o total nulo.
     */
    public static List<BigDecimal> calcularCuotas(BigDecimal total, int n) {
        if (total == null) throw new IllegalArgumentException("El monto total no puede ser nulo");
        if (n < 1) throw new IllegalArgumentException("La cantidad de cuotas debe ser al menos 1");

        BigDecimal cuotaBase = total.divide(new BigDecimal(n), 2, RoundingMode.HALF_UP);
        List<BigDecimal> cuotas = new ArrayList<>(n);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 1; i <= n; i++) {
            BigDecimal monto = (i == n) ? total.subtract(acumulado) : cuotaBase;
            acumulado = acumulado.add(monto);
            cuotas.add(monto);
        }
        return cuotas;
    }
}
