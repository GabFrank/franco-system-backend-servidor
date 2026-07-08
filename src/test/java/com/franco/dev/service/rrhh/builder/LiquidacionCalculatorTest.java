package com.franco.dev.service.rrhh.builder;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiquidacionCalculatorTest {

    private LiquidacionItem item(LiquidacionItemTipo tipo, String monto) {
        LiquidacionItem it = new LiquidacionItem();
        it.setTipo(tipo);
        it.setMonto(monto != null ? new BigDecimal(monto) : null);
        return it;
    }

    @Test
    void listaVacia_totalesEnCero() {
        LiquidacionCalculator.Totales t = LiquidacionCalculator.calcular(Collections.emptyList());
        assertEquals(0, BigDecimal.ZERO.compareTo(t.getTotalHaberes()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.getTotalDescuentos()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.getTotalNeto()));
    }

    @Test
    void soloHaberes_netoIgualHaberes() {
        List<LiquidacionItem> items = Arrays.asList(
                item(LiquidacionItemTipo.HABER, "2500000"),
                item(LiquidacionItemTipo.HABER, "150000"));
        LiquidacionCalculator.Totales t = LiquidacionCalculator.calcular(items);
        assertEquals(0, new BigDecimal("2650000").compareTo(t.getTotalHaberes()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.getTotalDescuentos()));
        assertEquals(0, new BigDecimal("2650000").compareTo(t.getTotalNeto()));
    }

    @Test
    void haberesYDescuentos_netoEsDiferencia() {
        // haberes: 2.500.000 + 150.000 = 2.650.000
        // descuentos: 225.000 (ips) + 200.000 (vale) + 100.000 (cuota) = 525.000
        // neto = 2.125.000
        List<LiquidacionItem> items = Arrays.asList(
                item(LiquidacionItemTipo.HABER, "2500000"),
                item(LiquidacionItemTipo.HABER, "150000"),
                item(LiquidacionItemTipo.DESCUENTO, "225000"),
                item(LiquidacionItemTipo.DESCUENTO, "200000"),
                item(LiquidacionItemTipo.DESCUENTO, "100000"));
        LiquidacionCalculator.Totales t = LiquidacionCalculator.calcular(items);
        assertEquals(0, new BigDecimal("2650000").compareTo(t.getTotalHaberes()));
        assertEquals(0, new BigDecimal("525000").compareTo(t.getTotalDescuentos()));
        assertEquals(0, new BigDecimal("2125000").compareTo(t.getTotalNeto()));
    }

    @Test
    void montosNulos_seIgnoran() {
        List<LiquidacionItem> items = Arrays.asList(
                item(LiquidacionItemTipo.HABER, "1000000"),
                item(LiquidacionItemTipo.HABER, null),
                item(LiquidacionItemTipo.DESCUENTO, null));
        LiquidacionCalculator.Totales t = LiquidacionCalculator.calcular(items);
        assertEquals(0, new BigDecimal("1000000").compareTo(t.getTotalHaberes()));
        assertEquals(0, BigDecimal.ZERO.compareTo(t.getTotalDescuentos()));
        assertEquals(0, new BigDecimal("1000000").compareTo(t.getTotalNeto()));
    }
}
