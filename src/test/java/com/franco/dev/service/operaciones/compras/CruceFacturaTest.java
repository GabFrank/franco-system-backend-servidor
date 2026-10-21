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
    void elMismoProductoEnDosRenglonesVaContraElItemDeSuMismaCantidad() {
        // La compra creada desde la factura tiene un ítem por renglón, creados en orden inverso:
        // el renglón de 50 no tiene que tomar el ítem de 30 por estar primero en la lista
        List<CruceFactura.Resultado> r = CruceFactura.cruzar(
                Arrays.asList(renglon(1, 7L, 50, 5218, 0), renglon(2, 7L, 30, 5218, 0)),
                Arrays.asList(item(100, 7, 30, 5218.0, false), item(101, 7, 50, 5218.0, false)), true);
        assertEquals(101L, r.get(0).pedidoItemId);
        assertEquals(100L, r.get(1).pedidoItemId);
        assertEquals(CruceFactura.Estado.OK, r.get(0).estado);
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

    // ------------------------------------------------------------------ La factura crea la compra

    @Test
    void sinPedidoLoReconocidoCoincideYLoDemasFaltaVincular() {
        List<CruceFactura.Resultado> r = CruceFactura.sinPedido(Arrays.asList(
                renglon(1, 7L, 80, 15651, 0), renglon(2, 7L, 8, 15651, 15651), renglon(3, null, 60, 2688, 0)));
        assertEquals(CruceFactura.Estado.OK, r.get(0).estado);
        assertEquals(15651.0, r.get(0).precioBase);
        assertFalse(r.get(0).requiereObservacion);
        assertTrue(r.get(1).bonificacion);
        assertEquals(0.0, r.get(1).precioBase);
        assertEquals(CruceFactura.Estado.SIN_PRODUCTO, r.get(2).estado);
    }

    @Test
    void laUltimaCompraSinIvaSeAvisa() {
        // Papas Sensei: la nota anterior se cargó a 14.228, la factura cobra 15.651 (+10 %)
        String aviso = CruceFactura.avisoUltimaCompra(15651, 14228.0, 10);
        assertNotNull(aviso);
        assertTrue(aviso.startsWith("IVA: "));
        assertTrue(aviso.contains("sin el IVA del 10 %"));
    }

    @Test
    void elMismoPrecioQueLaUltimaCompraNoAvisa() {
        assertNull(CruceFactura.avisoUltimaCompra(5218, 5218.0, 10));
        assertNull(CruceFactura.avisoUltimaCompra(5300, 5218.0, 10), "un cambio chico no es un aviso");
        assertNull(CruceFactura.avisoUltimaCompra(5218, null, 10), "sin compra anterior no hay con qué comparar");
    }

    @Test
    void unCambioGrandeQueNoEsElIvaSeAvisaComoPrecio() {
        String aviso = CruceFactura.avisoUltimaCompra(8000, 5218.0, 10);
        assertNotNull(aviso);
        assertTrue(aviso.startsWith("Precio más alto"));
    }

    // ------------------------------------------------------------------ El bulto por el precio
    // Casos de la factura real de GENFI (001-001-0055357) contra la última compra de cada producto en bodega.

    private static Double factor(double precio, Double ultimo, List<Double> presentaciones, String descripcion) {
        return CruceFactura.factorPorPrecio(precio, ultimo, presentaciones, Similitud.numerosDeBulto(descripcion));
    }

    @Test
    void saladixEsUnBultoDe30AunqueLaDescripcionDiga6() {
        // 141.865 / 4.729 = 30: lo dice el precio, no la descripción
        assertEquals(30.0, factor(141865, 4729.0, Arrays.asList(1.0), "SALADIX JAMON 6*100GR"));
    }

    @Test
    void bonobonEsElProductoDeLosNumerosDeLaDescripcion() {
        // 30 × 12 = 360 bombones; 455.150 / 360 = 1.264
        assertEquals(360.0, factor(455150, 1264.27, Arrays.asList(1.0, 18.0, 30.0), "BONOBON ORIGINAL 30X15G (12)"));
        assertEquals(400.0, factor(729485, 1823.7, Arrays.asList(1.0, 20.0), "TOPLINE STRONG 20X11G (20)"));
    }

    @Test
    void conElPrecioCambiadoIgualGanaElBultoDeLaDescripcion() {
        // 12 × 16 = 192 da 3.188 por unidad contra 2.869 de la última compra (+11 %): cierra dentro del margen
        assertEquals(192.0, factor(612128, 2869.38, Arrays.asList(1.0, 16.0), "TOPLINE SEVEN FRUT 12*16*14.7"));
    }

    @Test
    void unBultoQueArmaUnaPresentacionLeGanaALasUnidadesSueltas() {
        // 229.434 / 285 = 805,03: 12 bolsas de 67 = 804 le gana a 805 sueltas
        assertEquals(804.0, factor(229434, 285.0, Arrays.asList(1.0, 67.0), "BASTON VIENA 470gr"));
    }

    @Test
    void unPrecioUnitarioQueCierraNoSeConvierteEnBulto() {
        assertEquals(1.0, factor(5218, 5218.0, Arrays.asList(1.0, 30.0), "MAZZEI CRACKERS SALVADO 190G C/30"));
    }

    @Test
    void sinUltimaCompraOSiNingunoCierraNoSeAdivina() {
        assertNull(factor(141865, null, Arrays.asList(1.0), "SALADIX JAMON 6*100GR"));
        // GOMAS MOGUL: 273.997 contra 6.184 no cierra con 12, 144 ni ningún entero
        assertNull(factor(273997, 6184.0, Arrays.asList(1.0), "GOMAS MOGUL COLA 12X30G (12)"));
    }

    @Test
    void losNumerosDeBultoNoTomanGramajes() {
        assertEquals(Arrays.asList(30, 12), Similitud.numerosDeBulto("BONOBON ORIGINAL 30X15G (12)"));
        assertEquals(Arrays.asList(12, 24), Similitud.numerosDeBulto("MR.POPS EVOLU.CHERRY 12*24*18G"));
        assertEquals(Arrays.asList(24), Similitud.numerosDeBulto("MR.POPS EVOL.BLUEBER.24UN"));
        assertEquals(Arrays.asList(12), Similitud.numerosDeBulto("CAR M.PLUS SANDIA 12X29,4G"));
        assertEquals(Arrays.asList(10), Similitud.numerosDeBulto("CAR.MAST.FTA ARC.800G  FILI(10)"));
        assertTrue(Similitud.numerosDeBulto("RELLENOS FRUTALES 810gr").isEmpty());
    }
}
