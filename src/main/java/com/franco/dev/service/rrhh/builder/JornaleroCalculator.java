package com.franco.dev.service.rrhh.builder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo puro del salario base de un jornalero/diarista: valor_jornal × días
 * trabajados (de las jornadas del período). Mejora sobre Gourmet (§20 #6).
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 */
public final class JornaleroCalculator {

    private JornaleroCalculator() {
    }

    /**
     * salarioBase = valorJornal × díasTrabajados. Trata valorJornal nulo como
     * cero y díasTrabajados negativos como cero. Redondea a 2 decimales.
     */
    public static BigDecimal calcularSalarioBase(BigDecimal valorJornal, int diasTrabajados) {
        BigDecimal jornal = valorJornal != null ? valorJornal : BigDecimal.ZERO;
        int dias = Math.max(0, diasTrabajados);
        return jornal.multiply(new BigDecimal(dias)).setScale(2, RoundingMode.HALF_UP);
    }
}
