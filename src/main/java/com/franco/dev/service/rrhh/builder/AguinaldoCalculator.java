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
     * Meses PROYECTADOS del anio: cuantos meses va a haber trabajado al 31/12.
     * Si ingreso antes del anio: 12. Si ingreso durante el anio: 12 - mesIngreso + 1.
     * Si ingreso despues: 0. fechaIngreso nula asume anio completo.
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
     * Meses DEVENGADOS a una fecha de corte: los efectivamente trabajados hasta hoy.
     *
     * Antes solo existia el proyectado, que no miraba la fecha actual: calculado en
     * julio mostraba 12 meses (el aguinaldo completo) como si ya estuviera ganado.
     * Para un anio ya terminado ambos coinciden.
     */
    public static int mesesDevengados(int anio, LocalDate fechaIngreso, LocalDate corte) {
        if (corte == null) return mesesTrabajados(anio, fechaIngreso);
        if (corte.getYear() < anio) return 0;
        if (corte.getYear() > anio) return mesesTrabajados(anio, fechaIngreso);

        // El corte cae dentro del anio: se cuenta hasta el mes del corte inclusive.
        int mesDesde = (fechaIngreso != null && fechaIngreso.getYear() == anio)
                ? fechaIngreso.getMonthValue() : 1;
        if (fechaIngreso != null && fechaIngreso.getYear() > anio) return 0;
        int mesHasta = corte.getMonthValue();
        return Math.max(0, mesHasta - mesDesde + 1);
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
