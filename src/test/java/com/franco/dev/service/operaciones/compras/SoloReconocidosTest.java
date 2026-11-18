package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SoloReconocidosTest {

    private static CruceFactura.Resultado res(CruceFactura.Estado estado, double cantidad, double precio, boolean bonif) {
        CruceFactura.Resultado r = new CruceFactura.Resultado();
        r.estado = estado;
        r.cantidadBase = cantidad;
        r.precioBase = precio;
        r.bonificacion = bonif;
        return r;
    }

    @Test
    void sinReconocidosNoSePuede() {
        assertFalse(SoloReconocidos.puedeCrear(0, 5, false, false));
    }

    @Test
    void conBloqueoNoSePuede() {
        assertFalse(SoloReconocidos.puedeCrear(12, 3, true, false));
    }

    @Test
    void conNotaNoSePuede() {
        assertFalse(SoloReconocidos.puedeCrear(12, 3, false, true));
    }

    @Test
    void siTodoEstaReconocidoNoHaceFalta() {
        assertFalse(SoloReconocidos.puedeCrear(12, 0, false, false));
    }

    @Test
    void docePorTresSiSePuede() {
        assertTrue(SoloReconocidos.puedeCrear(12, 3, false, false));
    }

    @Test
    void montosSeparanReconocidoYSinCargar() {
        double[] m = SoloReconocidos.montos(Arrays.asList(
                res(CruceFactura.Estado.OK, 1, 1000, false),
                res(CruceFactura.Estado.OK, 1, 500, false),
                res(CruceFactura.Estado.SIN_PRODUCTO, 1, 240, false),
                res(CruceFactura.Estado.SIN_PRODUCTO, 6, 0, true)));
        assertEquals(1500.0, m[0], 0.001);
        assertEquals(240.0, m[1], 0.001);
    }
}
