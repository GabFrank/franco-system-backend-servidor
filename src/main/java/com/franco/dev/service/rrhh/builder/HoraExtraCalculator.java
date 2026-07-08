package com.franco.dev.service.rrhh.builder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo puro del monto de una hora extra (§ Fase 2).
 *   valorHora = sueldo / 30 / horasJornada
 *   horas     = minutos / 60
 *   monto     = valorHora × horas × (1 + recargo/100)
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 */
public final class HoraExtraCalculator {

    private static final BigDecimal SESENTA = new BigDecimal("60");
    private static final BigDecimal CIEN = new BigDecimal("100");

    private HoraExtraCalculator() {
    }

    /**
     * Monto de la hora extra. Devuelve cero si sueldo/horasJornada/minutos no
     * son válidos (> 0). recargoPorcentaje nulo se trata como cero. diasMes es el
     * divisor días/mes para el salario diario (parametrizable, típico 30).
     */
    public static BigDecimal calcularMonto(BigDecimal sueldoMensual, BigDecimal horasJornada,
                                           BigDecimal minutos, BigDecimal recargoPorcentaje, BigDecimal diasMes) {
        if (sueldoMensual == null || sueldoMensual.signum() <= 0) return BigDecimal.ZERO;
        if (horasJornada == null || horasJornada.signum() <= 0) return BigDecimal.ZERO;
        if (minutos == null || minutos.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal dias = (diasMes != null && diasMes.signum() > 0) ? diasMes : new BigDecimal("30");
        BigDecimal recargo = recargoPorcentaje != null ? recargoPorcentaje : BigDecimal.ZERO;

        // valorHora = sueldo / diasMes / horasJornada  (precisión intermedia amplia)
        BigDecimal valorHora = sueldoMensual
                .divide(dias, 6, RoundingMode.HALF_UP)
                .divide(horasJornada, 6, RoundingMode.HALF_UP);
        BigDecimal horas = minutos.divide(SESENTA, 6, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE.add(recargo.divide(CIEN, 6, RoundingMode.HALF_UP));

        return valorHora.multiply(horas).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
