package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaCancelacion;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Que muestra el detalle de la venta en «Cancelado por» (V274.1). */
class VentaResolverCancelacionTest {

    private static Venta venta(VentaEstado estado) {
        Venta v = new Venta();
        v.setEstado(estado);
        return v;
    }

    private static VentaCancelacion fila(VentaEstado estado) {
        VentaCancelacion c = new VentaCancelacion();
        c.setEstado(estado);
        return c;
    }

    @Test
    void canceladaConRegistroDeCancelacionLoDevuelve() {
        VentaCancelacion c = fila(VentaEstado.CANCELADA);
        assertSame(c, VentaResolver.cancelacionVigente(venta(VentaEstado.CANCELADA), c));
    }

    @Test
    void ventaNoCanceladaNoMuestraNada() {
        assertNull(VentaResolver.cancelacionVigente(venta(VentaEstado.CONCLUIDA), fila(VentaEstado.CANCELADA)));
    }

    @Test
    void canceladaSinRegistroOConUltimaReactivacionNoSeAtribuye() {
        // Cancelada antes de V274.1, o por una via que no pasa por cancelarVenta
        assertNull(VentaResolver.cancelacionVigente(venta(VentaEstado.CANCELADA), null));
        assertNull(VentaResolver.cancelacionVigente(venta(VentaEstado.CANCELADA), fila(VentaEstado.CONCLUIDA)));
    }
}
