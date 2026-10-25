package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.operaciones.VentaGraphQL.AjustesCobro;
import com.franco.dev.graphql.operaciones.VentaGraphQL.TotalesMovimientos;
import com.franco.dev.graphql.operaciones.VentaGraphQL.TotalesPorFormaPago;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cubre el calculo de totales del resumen de los reportes de ventas
 * (generic-list-venta, normal y detallado).
 */
class VentaGraphQLTotalesTest {

    private static final double DELTA = 0.001;

    private static FormaPago formaPago(String descripcion) {
        FormaPago fp = new FormaPago();
        fp.setDescripcion(descripcion);
        return fp;
    }

    private static CobroDetalle detalle(String tipo, String formaPago, Double valor, Double cambio) {
        CobroDetalle cd = new CobroDetalle();
        cd.setPago("PAGO".equals(tipo));
        cd.setVuelto("VUELTO".equals(tipo));
        cd.setDescuento("DESCUENTO".equals(tipo));
        cd.setAumento("AUMENTO".equals(tipo));
        cd.setFormaPago(formaPago(formaPago));
        cd.setValor(valor);
        cd.setCambio(cambio);
        return cd;
    }

    private static CobroDetalle enMoneda(CobroDetalle cd, String denominacion) {
        Moneda moneda = new Moneda();
        moneda.setDenominacion(denominacion);
        cd.setMoneda(moneda);
        return cd;
    }

    private static CobroDetalle gs(String tipo, Double valor) {
        return enMoneda(detalle(tipo, "EFECTIVO", valor, 1.0), "GUARANI");
    }

    @Test
    @DisplayName("#454: el Total final es lo cobrado, el aumento no se suma otra vez")
    void totalFinalNoSumaElAumentoDosVeces() {
        // Venta 180.000, cobro en efectivo 270.000 con aumento 90.000 (F8).
        TotalesMovimientos t = VentaGraphQL.totalesDeMovimientos(Arrays.asList(
                gs("PAGO", 270000.0),
                gs("AUMENTO", 90000.0)));

        assertEquals(270000.0, t.totalFinal, DELTA);
        assertEquals(90000.0, t.aumento, DELTA);
        assertEquals(270000.0, t.recibido, DELTA);
    }

    @Test
    @DisplayName("#454: un aumento histórico en negativo se muestra en positivo")
    void aumentoNegativoSeMuestraPositivo() {
        // Antes de 2024-10 la línea quedaba en negativo, pero el pago ya lo incluía.
        TotalesMovimientos t = VentaGraphQL.totalesDeMovimientos(Arrays.asList(
                gs("PAGO", 270000.0),
                gs("AUMENTO", -90000.0)));

        assertEquals(270000.0, t.totalFinal, DELTA);
        assertEquals(90000.0, t.aumento, DELTA);
    }

    @Test
    @DisplayName("#454: pago con vuelto, aumento en Gs y en moneda extranjera")
    void pagoVueltoYAumentoEnVariasMonedas() {
        TotalesMovimientos t = VentaGraphQL.totalesDeMovimientos(Arrays.asList(
                gs("PAGO", 100000.0),
                gs("VUELTO", -5000.0),
                gs("AUMENTO", 2000.0),
                enMoneda(detalle("PAGO", "EFECTIVO", 20.0, 1300.0), "REAL"),
                enMoneda(detalle("AUMENTO", "EFECTIVO", 1.0, 1300.0), "REAL"),
                enMoneda(detalle("PAGO", "EFECTIVO", 10.0, 7500.0), "DOLAR"),
                enMoneda(detalle("AUMENTO", "EFECTIVO", 0.5, 7500.0), "DOLAR")));

        double recibido = 100000.0 - 5000.0 + 20.0 * 1300.0 + 10.0 * 7500.0;
        assertEquals(95000.0, t.recibidoGs, DELTA);
        assertEquals(20.0, t.recibidoRs, DELTA);
        assertEquals(10.0, t.recibidoDs, DELTA);
        assertEquals(recibido, t.recibido, DELTA);
        assertEquals(recibido, t.totalFinal, DELTA);
        assertEquals(2000.0 + 1300.0 + 3750.0, t.aumento, DELTA);
    }

    @Test
    @DisplayName("#454: con descuento y aumento en la misma venta el Total final es lo cobrado")
    void descuentoYAumentoNoTocanElTotalFinal() {
        TotalesMovimientos t = VentaGraphQL.totalesDeMovimientos(Arrays.asList(
                gs("PAGO", 50000.0),
                gs("DESCUENTO", 3000.0),
                gs("AUMENTO", 1000.0)));

        assertEquals(50000.0, t.totalFinal, DELTA);
        assertEquals(3000.0, t.descuento, DELTA);
        assertEquals(1000.0, t.aumento, DELTA);
    }

    @Test
    @DisplayName("Cambio null vale 1; una moneda no reconocida no suma; lista null da cero")
    void casosBordeDeMovimientos() {
        TotalesMovimientos t = VentaGraphQL.totalesDeMovimientos(Arrays.asList(
                enMoneda(detalle("PAGO", "EFECTIVO", 15.0, null), "REAL"),
                enMoneda(detalle("PAGO", "EFECTIVO", 999.0, 1.0), "PESO"),
                detalle("DESCUENTO", "EFECTIVO", 500.0, 1.0)));

        assertEquals(15.0, t.recibido, DELTA);
        assertEquals(15.0, t.totalFinal, DELTA);
        assertEquals(0.0, t.descuento, DELTA);
        assertEquals(0.0, VentaGraphQL.totalesDeMovimientos(null).totalFinal, DELTA);
    }

    @Test
    @DisplayName("#454: el resumen toma el aumento negativo en valor absoluto")
    void ajustesDeTomaElAumentoEnValorAbsoluto() {
        List<CobroDetalle> detalles = Arrays.asList(
                detalle("PAGO", "EFECTIVO", 270000.0, 1.0),
                detalle("AUMENTO", "EFECTIVO", -90000.0, 1.0));

        AjustesCobro ajustes = VentaGraphQL.ajustesDe(detalles);
        assertEquals(90000.0, ajustes.aumento, DELTA);

        TotalesPorFormaPago totales = new TotalesPorFormaPago();
        VentaGraphQL.repartirPorFormaPago(detalles, formaPago("EFECTIVO"),
                180000.0 - ajustes.descuento + ajustes.aumento, totales);
        assertEquals(270000.0, totales.general, DELTA);
    }

    @Test
    @DisplayName("El descuento se toma del movimiento de cobro, no del total de la venta")
    void descuentoSaleDeLosMovimientos() {
        // Venta real 48479: total_gs 5500, descuento 500, cobrado 5000.
        List<CobroDetalle> detalles = Arrays.asList(
                detalle("PAGO", "EFECTIVO", 5000.0, 1.0),
                detalle("DESCUENTO", "EFECTIVO", 500.0, 1.0));

        AjustesCobro ajustes = VentaGraphQL.ajustesDe(detalles);

        assertEquals(500.0, ajustes.descuento, DELTA);
        assertEquals(0.0, ajustes.aumento, DELTA);

        TotalesPorFormaPago totales = new TotalesPorFormaPago();
        VentaGraphQL.repartirPorFormaPago(detalles, formaPago("EFECTIVO"),
                5500.0 - ajustes.descuento + ajustes.aumento, totales);

        assertEquals(5000.0, totales.general, DELTA);
        assertEquals(5000.0, totales.efectivo, DELTA);
    }

    @Test
    @DisplayName("El aumento suma al neto de la venta")
    void aumentoSumaAlNeto() {
        // Venta real 60247: total_gs 6000, aumento 250, cobrado 6250.
        List<CobroDetalle> detalles = Arrays.asList(
                detalle("PAGO", "EFECTIVO", 6250.0, 1.0),
                detalle("AUMENTO", "EFECTIVO", 250.0, 1.0));

        AjustesCobro ajustes = VentaGraphQL.ajustesDe(detalles);
        assertEquals(250.0, ajustes.aumento, DELTA);

        TotalesPorFormaPago totales = new TotalesPorFormaPago();
        VentaGraphQL.repartirPorFormaPago(detalles, formaPago("EFECTIVO"),
                6000.0 - ajustes.descuento + ajustes.aumento, totales);

        assertEquals(6250.0, totales.general, DELTA);
    }

    @Test
    @DisplayName("Un cobro con movimientos duplicados no infla el total")
    void movimientosDuplicadosNoInflanElTotal() {
        // Venta real 262022/sucursal 8: total_gs 83000 con dos juegos de
        // movimientos sobre el mismo cobro. Sumar los pagos daria 161000.
        List<CobroDetalle> detalles = Arrays.asList(
                detalle("PAGO", "EFECTIVO", 100000.0, 1.0),
                detalle("VUELTO", "EFECTIVO", -7000.0, 1.0),
                detalle("PAGO", "EFECTIVO", 100000.0, null),
                detalle("VUELTO", "EFECTIVO", -32000.0, null));

        AjustesCobro ajustes = VentaGraphQL.ajustesDe(detalles);
        TotalesPorFormaPago totales = new TotalesPorFormaPago();
        VentaGraphQL.repartirPorFormaPago(detalles, formaPago("EFECTIVO"),
                83000.0 - ajustes.descuento + ajustes.aumento, totales);

        assertEquals(83000.0, totales.general, DELTA);
        assertEquals(83000.0, totales.efectivo, DELTA);
    }

    @Test
    @DisplayName("Una venta mixta reparte el neto entre cada forma de pago")
    void ventaMixtaSeReparte() {
        List<CobroDetalle> detalles = Arrays.asList(
                detalle("PAGO", "EFECTIVO", 30000.0, 1.0),
                detalle("PAGO", "TARJETA", 70000.0, 1.0));

        TotalesPorFormaPago totales = new TotalesPorFormaPago();
        VentaGraphQL.repartirPorFormaPago(detalles, formaPago("EFECTIVO"), 100000.0, totales);

        assertEquals(100000.0, totales.general, DELTA);
        assertEquals(30000.0, totales.efectivo, DELTA);
        assertEquals(70000.0, totales.tarjeta, DELTA);
        // El reparto tiene que cerrar exactamente contra el total.
        assertEquals(totales.general,
                totales.efectivo + totales.tarjeta + totales.convenio
                        + totales.transferencia + totales.otros, DELTA);
    }

    @Test
    @DisplayName("El valor en moneda extranjera se convierte con su cambio")
    void monedaExtranjeraUsaElCambio() {
        List<CobroDetalle> detalles = Collections.singletonList(
                detalle("DESCUENTO", "EFECTIVO", 10.0, 7500.0));

        assertEquals(75000.0, VentaGraphQL.ajustesDe(detalles).descuento, DELTA);
    }

    @Test
    @DisplayName("Sin movimientos de cobro el neto va a la forma de pago de la venta")
    void sinMovimientosUsaLaFormaPagoDeLaVenta() {
        TotalesPorFormaPago totales = new TotalesPorFormaPago();
        VentaGraphQL.repartirPorFormaPago(null, formaPago("CONVENIO"), 12000.0, totales);

        assertEquals(12000.0, totales.general, DELTA);
        assertEquals(12000.0, totales.convenio, DELTA);
    }

    @Test
    @DisplayName("Una forma de pago desconocida cae en Otros")
    void formaPagoDesconocidaVaAOtros() {
        TotalesPorFormaPago totales = new TotalesPorFormaPago();
        VentaGraphQL.repartirPorFormaPago(
                Collections.singletonList(detalle("PAGO", "CHEQUE", 5000.0, 1.0)),
                formaPago("CHEQUE"), 5000.0, totales);

        assertEquals(5000.0, totales.general, DELTA);
        assertEquals(5000.0, totales.otros, DELTA);
    }
}
