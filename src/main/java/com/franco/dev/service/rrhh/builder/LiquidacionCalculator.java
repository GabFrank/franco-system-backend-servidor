package com.franco.dev.service.rrhh.builder;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculo puro de totales de una liquidacion de sueldo a partir de sus items.
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 */
public final class LiquidacionCalculator {

    private LiquidacionCalculator() {
    }

    public static class Totales {
        private final BigDecimal totalHaberes;
        private final BigDecimal totalDescuentos;
        private final BigDecimal totalNeto;

        public Totales(BigDecimal totalHaberes, BigDecimal totalDescuentos, BigDecimal totalNeto) {
            this.totalHaberes = totalHaberes;
            this.totalDescuentos = totalDescuentos;
            this.totalNeto = totalNeto;
        }

        public BigDecimal getTotalHaberes() { return totalHaberes; }
        public BigDecimal getTotalDescuentos() { return totalDescuentos; }
        public BigDecimal getTotalNeto() { return totalNeto; }
    }

    /**
     * totalHaberes = suma de items HABER; totalDescuentos = suma de items
     * DESCUENTO; totalNeto = haberes - descuentos. Ignora montos nulos.
     */
    public static Totales calcular(List<LiquidacionItem> items) {
        BigDecimal haberes = BigDecimal.ZERO;
        BigDecimal descuentos = BigDecimal.ZERO;
        if (items != null) {
            for (LiquidacionItem it : items) {
                BigDecimal monto = it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO;
                if (it.getTipo() == LiquidacionItemTipo.HABER) {
                    haberes = haberes.add(monto);
                } else if (it.getTipo() == LiquidacionItemTipo.DESCUENTO) {
                    descuentos = descuentos.add(monto);
                }
            }
        }
        return new Totales(haberes, descuentos, haberes.subtract(descuentos));
    }
}
