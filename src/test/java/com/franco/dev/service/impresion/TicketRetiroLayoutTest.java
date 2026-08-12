package com.franco.dev.service.impresion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.franco.dev.service.impresion.TicketRetiroLayout.COLUMNAS_58MM;
import static com.franco.dev.service.impresion.TicketRetiroLayout.COLUMNAS_80MM;
import static com.franco.dev.service.impresion.TicketRetiroLayout.centrar;
import static com.franco.dev.service.impresion.TicketRetiroLayout.columnaDoble;
import static com.franco.dev.service.impresion.TicketRetiroLayout.columnasPara;
import static com.franco.dev.service.impresion.TicketRetiroLayout.formatearCantidad;
import static com.franco.dev.service.impresion.TicketRetiroLayout.lineasDeItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El layout del ticket es lo que se rompe al cambiar de 58mm a 80mm, y no se
 * puede verificar contra una impresora real en CI. Estos tests fijan el ancho.
 */
class TicketRetiroLayoutTest {

    @Test
    void anchoDePapelDefineLasColumnas() {
        assertEquals(COLUMNAS_58MM, columnasPara(null), "sin ancho asume 58mm");
        assertEquals(COLUMNAS_58MM, columnasPara(58));
        assertEquals(COLUMNAS_80MM, columnasPara(80));
    }

    @Test
    void ningunaLineaExcedeElAncho() {
        for (int columnas : new int[]{COLUMNAS_58MM, COLUMNAS_80MM}) {
            List<String> lineas = lineasDeItem(
                    "BRAHMA SUB ZERO LATA 269 ML EDICION LIMITADA",
                    "7840050002561", "x12", "33", "543544", "07/07/2026", columnas);
            for (String linea : lineas) {
                assertTrue(linea.length() <= columnas,
                        "linea de " + linea.length() + " cols en papel de " + columnas + ": '" + linea + "'");
            }
        }
    }

    @Test
    void laCantidadNuncaSeTrunca() {
        // Codigo + factor largos: la izquierda cede, la cantidad sobrevive entera.
        String linea = columnaDoble("7840050002561999999 x120", "[ ] 1234", COLUMNAS_58MM);
        assertEquals(COLUMNAS_58MM, linea.length());
        assertTrue(linea.endsWith("[ ] 1234"), "termina con la cantidad: '" + linea + "'");
    }

    @Test
    void sinLoteNiVencimientoElItemOcupaDosLineas() {
        List<String> lineas = lineasDeItem("FANTA PIÑA 500ML", "7840058003690", "x1", "5", null, null, COLUMNAS_58MM);
        assertEquals(2, lineas.size());
        assertEquals("FANTA PIÑA 500ML", lineas.get(0));
        assertTrue(lineas.get(1).startsWith("7840058003690 x1"));
        assertTrue(lineas.get(1).endsWith("[ ] 5"));
    }

    @Test
    void conLoteYVencimientoAgregaLaTerceraLinea() {
        List<String> lineas = lineasDeItem("FANTA PIÑA 2LTS", "7840058004697", "x1", "2", "65465", "07/07/2026",
                COLUMNAS_58MM);
        assertEquals(3, lineas.size());
        assertTrue(lineas.get(2).startsWith("Lote 65465"));
        assertTrue(lineas.get(2).endsWith("Vto 07/07/2026"));
    }

    @Test
    void loteSoloOVencimientoSoloNoDejanEtiquetaHuerfana() {
        List<String> soloLote = lineasDeItem("X", "1", "x1", "1", "ABC", null, COLUMNAS_58MM);
        assertEquals("Lote ABC", soloLote.get(2).trim());

        List<String> soloVto = lineasDeItem("X", "1", "x1", "1", null, "01/01/2027", COLUMNAS_58MM);
        assertEquals("Vto 01/01/2027", soloVto.get(2).trim());

        // Lote vacio no cuenta como lote (evita imprimir "Lote" pelado).
        List<String> loteVacio = lineasDeItem("X", "1", "x1", "1", "  ", null, COLUMNAS_58MM);
        assertEquals(2, loteVacio.size());
    }

    @Test
    void cantidadSinDecimalesSobrantes() {
        assertEquals("10", formatearCantidad(10.0));
        assertEquals("0", formatearCantidad(null));
        assertEquals("2.5", formatearCantidad(2.5));
    }

    @Test
    void centrarNoDesbordaConTextoLargo() {
        String largo = "COMPROBANTE DE RETIRO DE MERCADERIA MUY LARGO";
        assertTrue(centrar(largo, COLUMNAS_58MM).length() <= COLUMNAS_58MM);
        assertTrue(centrar("CENTRAL", COLUMNAS_58MM).startsWith(" "), "queda centrado");
    }
}
