package com.franco.dev.service.rrhh.builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Calculo puro del aguinaldo anual (13er salario).
 * aguinaldo = (sueldo x meses_trabajados) / 12.
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 */
public final class AguinaldoCalculator {

    private AguinaldoCalculator() {
    }

    /**
     * Meses trabajados dentro del anio dado. Si ingreso antes del anio: 12.
     * Si ingreso durante el anio: 12 - mesIngreso + 1. Si ingreso despues del
     * anio: 0 (no corresponde aguinaldo). fechaIngreso nula asume anio completo.
     */
    public static int mesesTrabajados(int anio, LocalDate fechaIngreso) {
        if (fechaIngreso == null) return 12;
        if (fechaIngreso.getYear() > anio) return 0;
        if (fechaIngreso.getYear() == anio) {
            return 12 - fechaIngreso.getMonthValue() + 1;
        }
        return 12;
    }

    /**
     * monto = (sueldo x mesesTrabajados) / 12, redondeado a 2 decimales HALF_UP.
     * sueldo nulo se trata como cero.
     */
    public static BigDecimal calcularMonto(BigDecimal sueldo, int mesesTrabajados) {
        BigDecimal base = sueldo != null ? sueldo : BigDecimal.ZERO;
        int meses = Math.max(0, mesesTrabajados);
        return base.multiply(new BigDecimal(meses))
                .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }
}
