package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El cruce reproduce las reglas del diálogo «Editar ítem de nota» del desktop. Los números salen de la factura de
 * MARVI y de su nota anterior en bodega: papas Sensei pedidas a 14.228 y facturadas a 15.651 (+10 %), crackers a
 * 5.218 en los dos lados.
 */
class CruceFacturaTest {

    private static CruceFactura.Renglon renglon(long id, Long productoId, double cantidad, double precio, double descuento) {
        CruceFactura.Renglon r = new CruceFactura.Renglon();
        r.id = id;
        r.productoId = productoId;
        r.presentacionId = productoId != null ? productoId * 10 : null;
        r.presentacionDescripcion = "UNIDAD";
        r.factor = 1.0;
        r.cantidad = cantidad;
        r.precioUnitario = precio;
        r.descuentoUnitario = descuento;
        r.porcentajeDescuento = precio > 0 ? descuento / precio * 100 : 0.0;
        r.tasaIva = 10;
        r.ivaProducto = 10;
        return r;
    }

    private static CruceFactura.ItemPedido item(long id, long productoId, double pendiente, Double precio, boolean bonif) {
        CruceFactura.ItemPedido i = new CruceFactura.ItemPedido();
        i.id = id;
        i.productoId = productoId;
        i.presentacionId = productoId * 10;
        i.presentacionDescripcion = "UNIDAD";
        i.cantidadPendiente = pendiente;
        i.precioUnitario = precio;
        i.bonificacion = bonif;
        return i;
    }

    @Test
    void mismoProductoCantidadYPrecioCoincide() {
        List<CruceFactura.Resultado> r = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, 7L, 30, 5218, 0)),
                Collections.singletonList(item(100, 7, 30, 5218.0, false)), true);
        assertEquals(CruceFactura.Estado.OK, r.get(0).estado);
        assertEquals(100L, r.get(0).pedidoItemId);
        assertEquals(5218.0, r.get(0).precioBase);
        assertFalse(r.get(0).requiereObservacion);
        assertTrue(r.get(0).avisosIva.isEmpty());
    }

    @Test
    void precioDelPedidoMasDiezPorCientoAvisaQueElPedidoEstabaSinIva() {
        List<CruceFactura.Resultado> r = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, 7L, 80, 15651, 0)),
                Collections.singletonList(item(100, 7, 80, 14228.0, false)), true);
        CruceFactura.Resultado res = r.get(0);
        assertEquals(CruceFactura.Estado.DIFERENCIA, res.estado);
        assertTrue(res.requiereObservacion, "una diferencia de precio exige observación, como en el desktop");
        assertTrue(res.observacionFaltante);
        assertEquals(1, res.diferencias.size());
        assertTrue(res.diferencias.get(0).startsWith("Precio"));
        assertEquals(1, res.avisosIva.size());
        assertTrue(res.avisosIva.get(0).contains("sin IVA"));
    }

    @Test
    void conObservacionLaDiferenciaQuedaJustificada() {
        CruceFactura.Renglon conObs = renglon(1, 7L, 80, 15651, 0);
        conObs.observacion = "Subió el precio";
        CruceFactura.Resultado res = CruceFactura.cruzar(Collections.singletonList(conObs),
                Collections.singletonList(item(100, 7, 80, 14228.0, false)), true).get(0);
        assertTrue(res.requiereObservacion);
        assertFalse(res.observacionFaltante);
    }

    @Test
    void unPrecioDistintoQueNoEsElIvaNoAvisaDeIva() {
        CruceFactura.Resultado res = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, 7L, 30, 6000, 0)),
                Collections.singletonList(item(100, 7, 30, 5218.0, false)), true).get(0);
        assertEquals(CruceFactura.Estado.DIFERENCIA, res.estado);
        assertTrue(res.avisosIva.isEmpty());
    }

    @Test
    void enOtraMonedaElPrecioNoSeCompara() {
        CruceFactura.Resultado res = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, 7L, 30, 2.5, 0)),
                Collections.singletonList(item(100, 7, 30, 5218.0, false)), false).get(0);
        assertEquals(CruceFactura.Estado.OK, res.estado);
    }

    @Test
    void laBonificacionNoSeComeElPendienteDelItemCobrado() {
        // 80 cobradas + 8 sin cargo del mismo producto; el pedido solo tenía las 80
        List<CruceFactura.Resultado> r = CruceFactura.cruzar(
                Arrays.asList(renglon(2, 7L, 8, 15651, 15651), renglon(1, 7L, 80, 15651, 0)),
                Collections.singletonList(item(100, 7, 80, 15651.0, false)), true);
        CruceFactura.Resultado bonif = r.get(0);
        CruceFactura.Resultado cobrado = r.get(1);
        assertEquals(CruceFactura.Estado.OK, cobrado.estado, "las 80 cobradas cubren el pendiente justo");
        assertEquals(100L, cobrado.pedidoItemId);
        assertTrue(bonif.bonificacion);
        assertEquals(CruceFactura.Estado.OK, bonif.estado);
        assertNull(bonif.pedidoItemId, "una bonificación que el pedido no esperaba no va contra el ítem cobrado");
        assertEquals(0.0, bonif.precioBase);
        assertEquals(1, bonif.avisos.size());
    }

    @Test
    void laBonificacionVaContraElItemBonificadoDelPedido() {
        List<CruceFactura.Resultado> r = CruceFactura.cruzar(
                Arrays.asList(renglon(1, 7L, 80, 15651, 0), renglon(2, 7L, 8, 15651, 15651)),
                Arrays.asList(item(100, 7, 80, 15651.0, false), item(101, 7, 8, 0.0, true)), true);
        assertEquals(100L, r.get(0).pedidoItemId);
        assertEquals(101L, r.get(1).pedidoItemId);
        assertEquals(CruceFactura.Estado.OK, r.get(1).estado);
    }

    @Test
    void menosCantidadQueLaPendienteEsDiferencia() {
        CruceFactura.Resultado res = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, 7L, 60, 5218, 0)),
                Collections.singletonList(item(100, 7, 80, 5218.0, false)), true).get(0);
        assertEquals(CruceFactura.Estado.DIFERENCIA, res.estado);
        assertEquals("Cantidad: faltaban 80 u. → factura 60 u.", res.diferencias.get(0));
    }

    @Test
    void dosRenglonesDelMismoProductoConsumenElPendienteEnOrden() {
        List<CruceFactura.Resultado> r = CruceFactura.cruzar(
                Arrays.asList(renglon(1, 7L, 50, 5218, 0), renglon(2, 7L, 30, 5218, 0)),
                Collections.singletonList(item(100, 7, 80, 5218.0, false)), true);
        assertEquals(80.0, r.get(0).pedidoCantidadPendiente);
        assertEquals(30.0, r.get(1).pedidoCantidadPendiente);
        assertEquals(CruceFactura.Estado.OK, r.get(1).estado);
    }

    @Test
    void productoQueNoEstabaEnElPedidoSeAgregaConObservacion() {
        CruceFactura.Resultado res = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, 9L, 60, 2688, 0)),
                Collections.singletonList(item(100, 7, 80, 5218.0, false)), true).get(0);
        assertEquals(CruceFactura.Estado.FUERA_DE_PEDIDO, res.estado);
        assertTrue(res.requiereObservacion);
        assertNull(res.pedidoItemId);
    }

    @Test
    void sinProductoNoSeCruza() {
        CruceFactura.Resultado res = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, null, 96, 2000, 0)),
                Collections.singletonList(item(100, 7, 80, 5218.0, false)), true).get(0);
        assertEquals(CruceFactura.Estado.SIN_PRODUCTO, res.estado);
        assertEquals(96.0, res.cantidadBase);
        assertEquals(2000.0, res.precioBase);
    }

    @Test
    void elFactorPasaLaFacturaAUnidadesBase() {
        // El proveedor factura cajas de 20: 4 cajas a 300.000 son 80 unidades a 15.000
        CruceFactura.Renglon caja = renglon(1, 7L, 4, 300000, 0);
        caja.factor = 20.0;
        CruceFactura.Resultado res = CruceFactura.cruzar(Collections.singletonList(caja),
                Collections.singletonList(item(100, 7, 80, 15000.0, false)), true).get(0);
        assertEquals(80.0, res.cantidadBase);
        assertEquals(15000.0, res.precioBase);
        assertEquals(CruceFactura.Estado.OK, res.estado);
    }

    @Test
    void ivaDistintoAlDelProductoAvisaSinPedirObservacion() {
        CruceFactura.Renglon r = renglon(1, 7L, 30, 5218, 0);
        r.ivaProducto = 5;
        CruceFactura.Resultado res = CruceFactura.cruzar(Collections.singletonList(r),
                Collections.singletonList(item(100, 7, 30, 5218.0, false)), true).get(0);
        assertEquals(CruceFactura.Estado.OK, res.estado);
        assertFalse(res.requiereObservacion);
        assertEquals("En el sistema el producto tiene IVA 5 % y la factura dice IVA 10 %.", res.avisosIva.get(0));
    }

    @Test
    void elDescuentoParcialBajaElPrecioDeLaNota() {
        CruceFactura.Resultado res = CruceFactura.cruzar(
                Collections.singletonList(renglon(1, 7L, 10, 10000, 500)),
                Collections.singletonList(item(100, 7, 10, 9500.0, false)), true).get(0);
        assertFalse(res.bonificacion);
        assertEquals(9500.0, res.precioBase);
        assertEquals(CruceFactura.Estado.OK, res.estado);
    }
}
