package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Una factura solo se puede cargar a un producto que se vende: un vínculo aprendido o un código de barras que
 * apunta a un producto o presentación inactivos no cuenta como reconocido.
 */
class PresentacionUsableTest {

    private static Presentacion presentacion(Boolean productoActivo, Boolean presentacionActiva) {
        Producto producto = new Producto();
        producto.setActivo(productoActivo);
        Presentacion p = new Presentacion();
        p.setProducto(producto);
        p.setActivo(presentacionActiva);
        return p;
    }

    @Test
    void productoYPresentacionActivosSirven() {
        assertTrue(ComprasMobileService.presentacionUsable(presentacion(true, true)));
    }

    @Test
    void sinMarcaDeActivoSeTomaComoActivo() {
        assertTrue(ComprasMobileService.presentacionUsable(presentacion(null, null)));
    }

    @Test
    void productoInactivoNoSirve() {
        assertFalse(ComprasMobileService.presentacionUsable(presentacion(false, true)));
    }

    @Test
    void presentacionInactivaNoSirve() {
        assertFalse(ComprasMobileService.presentacionUsable(presentacion(true, false)));
    }

    @Test
    void sinPresentacionOSinProductoNoSirve() {
        assertFalse(ComprasMobileService.presentacionUsable(null));
        assertFalse(ComprasMobileService.presentacionUsable(new Presentacion()));
    }
}
