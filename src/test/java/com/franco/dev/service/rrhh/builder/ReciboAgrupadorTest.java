package com.franco.dev.service.rrhh.builder;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReciboAgrupadorTest {

    private LiquidacionItem item(String codigo, String desc, String monto, LiquidacionItemTipo tipo) {
        LiquidacionItem it = new LiquidacionItem();
        it.setCodigo(codigo);
        it.setDescripcion(desc);
        it.setMonto(new BigDecimal(monto));
        it.setTipo(tipo);
        return it;
    }

    private BigDecimal sumaDescuentos(List<LiquidacionItem> items) {
        BigDecimal t = BigDecimal.ZERO;
        for (LiquidacionItem i : items) {
            if (i.getTipo() == LiquidacionItemTipo.DESCUENTO) t = t.add(i.getMonto());
        }
        return t;
    }

    @Test
    void agrupaVariasCuotasDeCreditoEnUnaSolaLinea() {
        List<LiquidacionItem> items = Arrays.asList(
                item("SALARIO_BASE", "SALARIO BASE", "3100000", LiquidacionItemTipo.HABER),
                item("CREDITO_CONVENIO_CUOTA", "CUOTA CREDITO - venta #1", "10000", LiquidacionItemTipo.DESCUENTO),
                item("CREDITO_CONVENIO_CUOTA", "CUOTA CREDITO - venta #2", "21500", LiquidacionItemTipo.DESCUENTO),
                item("CREDITO_CONVENIO_CUOTA", "CUOTA CREDITO - venta #3", "98460", LiquidacionItemTipo.DESCUENTO));

        List<LiquidacionItem> out = ReciboAgrupador.consolidarCuotasCredito(items);

        assertEquals(2, out.size(), "deberian quedar el salario y una sola linea de credito");
        LiquidacionItem agrupado = out.get(1);
        assertEquals("COMPRAS A CREDITO (3 CUOTAS)", agrupado.getDescripcion());
        assertEquals(0, new BigDecimal("129960").compareTo(agrupado.getMonto()));
        assertEquals(LiquidacionItemTipo.DESCUENTO, agrupado.getTipo());
    }

    /** Lo que no puede pasar nunca: que agrupar cambie lo que el funcionario cobra. */
    @Test
    void noCambiaElTotalDescontado() {
        List<LiquidacionItem> items = Arrays.asList(
                item("SALARIO_BASE", "SALARIO BASE", "3100000", LiquidacionItemTipo.HABER),
                item("IPS_DESCUENTO", "DESCUENTO IPS", "279000", LiquidacionItemTipo.DESCUENTO),
                item("CREDITO_CONVENIO_CUOTA", "CUOTA CREDITO - venta #1", "10000", LiquidacionItemTipo.DESCUENTO),
                item("CREDITO_CONVENIO_CUOTA", "CUOTA CREDITO - venta #2", "21500", LiquidacionItemTipo.DESCUENTO));

        assertEquals(0, sumaDescuentos(items).compareTo(
                sumaDescuentos(ReciboAgrupador.consolidarCuotasCredito(items))));
    }

    /** Lo pedido fue consolidar credito, NO prestamos. */
    @Test
    void noTocaLasCuotasDePrestamo() {
        List<LiquidacionItem> items = Arrays.asList(
                item("PRESTAMO_CUOTA", "CUOTA #2 PRESTAMO #1", "400000", LiquidacionItemTipo.DESCUENTO),
                item("PRESTAMO_CUOTA", "CUOTA #3 PRESTAMO #1", "400000", LiquidacionItemTipo.DESCUENTO));

        List<LiquidacionItem> out = ReciboAgrupador.consolidarCuotasCredito(items);

        assertEquals(2, out.size());
        assertEquals("CUOTA #2 PRESTAMO #1", out.get(0).getDescripcion());
        assertEquals("CUOTA #3 PRESTAMO #1", out.get(1).getDescripcion());
    }

    /** Con una sola cuota, agrupar perderia el detalle a cambio de nada. */
    @Test
    void conUnaSolaCuotaDejaElDetalle() {
        List<LiquidacionItem> items = Arrays.asList(
                item("SALARIO_BASE", "SALARIO BASE", "3100000", LiquidacionItemTipo.HABER),
                item("CREDITO_CONVENIO_CUOTA", "CUOTA CREDITO - venta #7", "50000", LiquidacionItemTipo.DESCUENTO));

        List<LiquidacionItem> out = ReciboAgrupador.consolidarCuotasCredito(items);

        assertEquals(2, out.size());
        assertEquals("CUOTA CREDITO - venta #7", out.get(1).getDescripcion());
    }

    @Test
    void soportaListaVaciaYSinCuotas() {
        assertTrue(ReciboAgrupador.consolidarCuotasCredito(new ArrayList<>()).isEmpty());
        List<LiquidacionItem> soloSalario = Arrays.asList(
                item("SALARIO_BASE", "SALARIO BASE", "3100000", LiquidacionItemTipo.HABER));
        assertEquals(1, ReciboAgrupador.consolidarCuotasCredito(soloSalario).size());
    }
}
