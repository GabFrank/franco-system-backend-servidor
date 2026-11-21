package com.franco.dev.service.configuraciones;

import com.franco.dev.domain.configuraciones.enums.TipoOperacion;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorialCambiosFormatoTest {

    @Test
    void valoresComoLosLeeUnaPersona() {
        assertEquals("96.000", HistorialCambiosFormato.valor("96000.0"));
        assertEquals("9.999,83", HistorialCambiosFormato.valor("9999.83333333333"));
        assertEquals("Sí", HistorialCambiosFormato.valor("true"));
        assertEquals("No", HistorialCambiosFormato.valor("false"));
        assertEquals("30/09/2028", HistorialCambiosFormato.valor("2028-09-30T00:00"));
        assertEquals("16/11/2026 14:28", HistorialCambiosFormato.valor("2026-11-16T14:28:17.314188738"));
        assertEquals("Concluido", HistorialCambiosFormato.valor("CONCLUIDO"));
        assertEquals("N° 12", HistorialCambiosFormato.valor("Producto (ID: 12)"));
        assertEquals("PROMO 2X1", HistorialCambiosFormato.valor("PROMO 2X1"));
        assertNull(HistorialCambiosFormato.valor(null));
        assertEquals("7840127001503", HistorialCambiosFormato.valor("codigo", "7840127001503"));
    }

    @Test
    void reconoceReferenciasAOtrasTablas() {
        assertTrue(HistorialCambiosFormato.esReferencia("TipoPrecio (ID: 3)"));
        assertTrue(HistorialCambiosFormato.esReferencia("Presentacion (sin ID)"));
        assertFalse(HistorialCambiosFormato.esReferencia("COCA COLA (2L)"));
    }

    @Test
    void etiquetasDeCampos() {
        assertEquals("Último precio de compra", HistorialCambiosFormato.etiquetaCampo("ultimoPrecioCompra"));
        assertEquals("Cantidad contada", HistorialCambiosFormato.etiquetaCampo("cantidadFisica"));
        // Uno sin etiqueta propia: camelCase en palabras
        assertEquals("Codigo interno", HistorialCambiosFormato.etiquetaCampo("codigoInterno"));
        assertFalse(HistorialCambiosFormato.campoVisible("creadoEn"));
        assertTrue(HistorialCambiosFormato.campoVisible("precio"));
    }

    @Test
    void accionEnPalabras() {
        assertEquals("Cambió el precio", HistorialCambiosFormato.accion("PRECIO_POR_SUCURSAL", TipoOperacion.UPDATE,
                Collections.singletonMap("precio", "98000.0")));
        assertEquals("Modificó un precio", HistorialCambiosFormato.accion("PRECIO_POR_SUCURSAL", TipoOperacion.UPDATE,
                Collections.singletonMap("principal", "true")));
        assertEquals("Agregó un código de barras", HistorialCambiosFormato.accion("CODIGO", TipoOperacion.INSERT, null));
        assertEquals("Concluyó el inventario", HistorialCambiosFormato.accion("INVENTARIO", TipoOperacion.UPDATE,
                Collections.singletonMap("estado", "CONCLUIDO")));
        assertEquals("Desactivó el producto", HistorialCambiosFormato.accion("PRODUCTO", TipoOperacion.UPDATE,
                Collections.singletonMap("activo", "false")));
        assertEquals("Eliminó un registro de otra cosa", HistorialCambiosFormato.accion("OTRA_COSA", TipoOperacion.DELETE, null));
    }

    static class Producto { Long id; Producto(Long id) { this.id = id; } public Long getId() { return id; } }
    static class Presentacion { Producto producto; Presentacion(Producto p) { producto = p; } public Producto getProducto() { return producto; } }
    static class Precio { Presentacion presentacion; Precio(Presentacion p) { presentacion = p; } public Presentacion getPresentacion() { return presentacion; } }
    static class Costo { Producto producto; Costo(Producto p) { producto = p; } public Producto getProducto() { return producto; } }

    @Test
    void productoDeCadaEntidad() {
        assertEquals(7L, HistorialCambiosFormato.productoIdDe(new Producto(7L), "PRODUCTO", 7L));
        assertEquals(5L, HistorialCambiosFormato.productoIdDe(new Costo(new Producto(5L)), "COSTO_POR_PRODUCTO", 1L));
        assertEquals(9L, HistorialCambiosFormato.productoIdDe(new Precio(new Presentacion(new Producto(9L))),
                "PRECIO_POR_SUCURSAL", 1L));
        assertNull(HistorialCambiosFormato.productoIdDe(new Object(), "INVENTARIO", 1L));
    }
}
