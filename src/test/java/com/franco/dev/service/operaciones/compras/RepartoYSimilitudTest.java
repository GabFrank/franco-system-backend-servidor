package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RepartoYSimilitudTest {

    @Test
    void repartoEnteroSumaExactoYNoDejaDecimales() {
        double[] p = Reparto.proporcional(80, new double[]{1, 1, 1});
        assertEquals(80, Arrays.stream(p).sum());
        for (double x : p) assertEquals(Math.rint(x), x);
        assertArrayEquals(new double[]{27, 27, 26}, p);
    }

    @Test
    void repartoSigueLaProporcionDelPedido() {
        // Pedido: 60 para una sucursal y 20 para otra; la factura trae 40
        assertArrayEquals(new double[]{30, 10}, Reparto.proporcional(40, new double[]{60, 20}));
    }

    @Test
    void repartoConDecimalesSumaExacto() {
        double[] p = Reparto.proporcional(10.5, new double[]{1, 2});
        assertEquals(10.5, p[0] + p[1], 1e-9);
    }

    @Test
    void sinPesosSeReparteEnPartesIguales() {
        assertArrayEquals(new double[]{5, 5}, Reparto.proporcional(10, new double[]{0, 0}));
    }

    @Test
    void distanciaEntreCodigos() {
        assertEquals(0, Similitud.distancia("7841503031145", "7841503031145"));
        assertEquals(2, Similitud.distancia("7841503031145", "7841503031138"));
        assertEquals(1, Similitud.distancia("784150300417", "7841503000417"));
    }

    @Test
    void laDescripcionDelProveedorSeLimpiaParaBuscar() {
        assertEquals(Arrays.asList("SENSEI PAPAS FRITAS PIZZA", "SENSEI PAPAS"),
                Similitud.consultasDescripcion("SENSEI PAPAS FRITAS PIZZA 140gr.C/20"));
        assertEquals(Arrays.asList("POLIFOAM ESPONJA ACERO INOX", "POLIFOAM ESPONJA"),
                Similitud.consultasDescripcion("POLIFOAM ESPONJA DE ACERO INOX C/60"));
        assertTrue(Similitud.consultasDescripcion("  ").isEmpty());
    }

    @Test
    void elRucSeComparaSinDvNiCeros() {
        assertTrue(ComprasMobileService.mismoRuc("80062507-2", "80062507"));
        assertTrue(ComprasMobileService.mismoRuc("080062507", "80062507"));
        assertTrue(ComprasMobileService.mismoRuc("80.062.507-2", "80062507"));
        assertFalse(ComprasMobileService.mismoRuc("80062508", "80062507"));
        assertFalse(ComprasMobileService.mismoRuc(null, "80062507"));
    }
}
