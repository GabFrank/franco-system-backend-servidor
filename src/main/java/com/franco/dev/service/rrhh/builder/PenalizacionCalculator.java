package com.franco.dev.service.rrhh.builder;

import java.math.BigDecimal;

/**
 * Calculo puro del monto de una penalizacion por tardanza.
 * Espejo de la formula de FRC Gourmet: monto = fijo + porMinuto x minutos.
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 */
public final class PenalizacionCalculator {

    private PenalizacionCalculator() {
    }

    /**
     * monto = montoFijo + (montoPorMinuto x minutosTardanza).
     * Trata montos nulos como cero y minutos negativos/nulos como cero.
     */
    public static BigDecimal calcularMontoTardanza(BigDecimal montoFijo, BigDecimal montoPorMinuto, long minutosTardanza) {
        BigDecimal fijo = montoFijo != null ? montoFijo : BigDecimal.ZERO;
        BigDecimal porMin = montoPorMinuto != null ? montoPorMinuto : BigDecimal.ZERO;
        long minutos = Math.max(0, minutosTardanza);
        return fijo.add(porMin.multiply(new BigDecimal(minutos)));
    }
}
