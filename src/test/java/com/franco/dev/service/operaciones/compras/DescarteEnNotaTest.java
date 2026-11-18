package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los renglones descartados («No cargar», PWA #124) no se reconocen, no cuentan como pendientes y no entran a la nota;
 * la factura avisa cuáles quedaron afuera, por cuánto y por qué.
 */
class DescarteEnNotaTest {

    private static DocumentoRecibidoItem descartado(long id, String descripcion, String motivo,
                                                    double cantidad, double precio, double descuento, Double total) {
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setId(id);
        r.setOrden((int) id);
        r.setDescripcion(descripcion);
        r.setCantidad(cantidad);
        r.setPrecioUnitario(precio);
        r.setDescuentoUnitario(descuento);
        r.setTotal(total);
        r.setDescartado(true);
        r.setMotivoDescarte(motivo);
        return r;
    }

    private static DocumentoRecibidoItem reconocido(long id, long productoId, double cantidad, double precio) {
        DocumentoRecibidoItem r = new DocumentoRecibidoItem();
        r.setId(id);
        r.setOrden((int) id);
        r.setDescripcion("PRODUCTO " + productoId);
        r.setCantidad(cantidad);
        r.setPrecioUnitario(precio);
        r.setDescuentoUnitario(0.0);
        r.setTotal(cantidad * precio);
        r.setTasaIva(10);
        r.setFactor(1.0);
        Producto p = new Producto();
        p.setId(productoId);
        p.setIva(10);
        r.setProducto(p);
        Presentacion pr = new Presentacion();
        pr.setId(productoId * 10);
        pr.setDescripcion("UNIDAD");
        r.setPresentacion(pr);
        return r;
    }

    @Test
    void observacionNombraCadaDescarte() {
        List<DocumentoRecibidoItem> descartes = Arrays.asList(
                descartado(1, "MECANO TUERCA", "no lo vendemos", 5, 30000, 0, 150000.0),
                // Sin total: cantidad × (precio − descuento) = 3 × 30.000
                descartado(2, "EXHIBIDOR DURACELL", "es de regalo", 3, 32000, 2000, null));
        assertEquals("No incluye 2 renglones de la factura descartados por Gs. 240.000: MECANO TUERCA (no lo vendemos); "
                + "EXHIBIDOR DURACELL (es de regalo)", ComprasMobileService.observacionDescartes(descartes, "Gs."));
    }

    @Test
    void unSoloDescarteVaEnSingular() {
        List<DocumentoRecibidoItem> descartes = Collections.singletonList(
                descartado(1, "MECANO TUERCA", "no lo vendemos", 5, 30000, 0, 0.0));
        assertEquals("No incluye 1 renglón de la factura descartado por Gs. 150.000: MECANO TUERCA (no lo vendemos)",
                ComprasMobileService.observacionDescartes(descartes, "Gs."));
    }

    @Test
    void sinDescartesNoHayObservacion() {
        assertNull(ComprasMobileService.observacionDescartes(Collections.emptyList(), "Gs."));
        assertNull(ComprasMobileService.observacionDescartes(null, "Gs."));
    }

    @Test
    void losDescartadosQuedanFueraDeLosVigentes() {
        DocumentoRecibidoItem ok = reconocido(1, 7, 30, 5218);
        DocumentoRecibidoItem fuera = descartado(2, "MECANO TUERCA", "no lo vendemos", 5, 30000, 0, 150000.0);
        DocumentoRecibidoItem sinMarca = reconocido(3, 8, 1, 100);
        sinMarca.setDescartado(null);
        List<DocumentoRecibidoItem> todos = Arrays.asList(ok, fuera, sinMarca);
        assertEquals(Arrays.asList(ok, sinMarca), ComprasMobileService.vigentes(todos));
        assertEquals(Collections.singletonList(fuera), ComprasMobileService.descartados(todos));
    }

    @Test
    void facturaConPendienteDescartadoPuedeConfirmar() {
        List<DocumentoRecibidoItem> todos = Arrays.asList(
                reconocido(1, 7, 30, 5218),
                descartado(2, "MECANO TUERCA", "no lo vendemos", 5, 30000, 0, 150000.0));
        CruceFactura.ItemPedido item = new CruceFactura.ItemPedido();
        item.id = 100;
        item.productoId = 7;
        item.presentacionId = 70L;
        item.presentacionDescripcion = "UNIDAD";
        item.cantidadPendiente = 30;
        item.precioUnitario = 5218.0;

        List<CruceFactura.Resultado> conTodos = CruceFactura.cruzar(
                todos.stream().map(ComprasMobileService::entradaDe).collect(Collectors.toList()),
                Collections.singletonList(item), true);
        assertFalse(ComprasMobileService.puedeConfirmarConPedido(false, false, conTodos),
                "sin filtrar, el descartado cuenta como renglón sin producto");

        List<CruceFactura.Resultado> vigentes = CruceFactura.cruzar(
                ComprasMobileService.vigentes(todos).stream().map(ComprasMobileService::entradaDe).collect(Collectors.toList()),
                Collections.singletonList(item), true);
        assertEquals(1, vigentes.size());
        assertEquals(CruceFactura.Estado.OK, vigentes.get(0).estado);
        assertTrue(ComprasMobileService.puedeConfirmarConPedido(false, false, vigentes));
        assertFalse(ComprasMobileService.puedeConfirmarConPedido(true, false, vigentes), "ya tiene nota");
        assertFalse(ComprasMobileService.puedeConfirmarConPedido(false, true, vigentes), "bloqueada");
        assertFalse(ComprasMobileService.puedeConfirmarConPedido(false, false, Collections.emptyList()), "sin renglones");
    }
}
