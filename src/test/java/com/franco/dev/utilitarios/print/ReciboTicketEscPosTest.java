package com.franco.dev.utilitarios.print;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Texto del ticket ESC/POS de los recibos RRHH, leido del payload tal como le llega a la
 * impresora: se decodifica el base64 y se descartan los comandos de estilo.
 */
public class ReciboTicketEscPosTest {

    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;

    @Test
    void imprimeElEncabezadoConceptoMontoEn58y80() {
        for (int ancho : new int[]{58, 80}) {
            List<String> lineas = lineas(ticket("RECIBO DE VALE Nro. 12", null, ancho,
                    new ReciboTicketEscPos.Row("ANTICIPO SUELDO (2026-09-10)", "150.000")));
            boolean hayEncabezado = lineas.stream()
                    .anyMatch(l -> l.startsWith("Concepto") && l.trim().endsWith("Monto"));
            assertTrue(hayEncabezado, ancho + "mm sin encabezado Concepto | Monto: " + lineas);
            // El encabezado va antes de la primera fila.
            int enc = indiceQueEmpieza(lineas, "Concepto");
            int fila = indiceQueEmpieza(lineas, "ANTICIPO SUELDO");
            assertTrue(enc >= 0 && enc < fila, "el encabezado tiene que ir antes de las filas: " + lineas);
        }
    }

    @Test
    void imprimeLaObservacionNormalizada() {
        List<String> lineas = lineas(ticket("RECIBO DE VALE Nro. 12", "  pago del\n colegio \t y remedios ", 80,
                new ReciboTicketEscPos.Row("ANTICIPO SUELDO (2026-09-10)", "150.000")));
        assertTrue(lineas.contains("Obs.: pago del colegio y remedios"),
                "la observacion tiene que salir en una linea propia y sin saltos: " + lineas);
    }

    @Test
    void conceptoConSaltosDeLineaQuedaEnColumnas() {
        List<String> lineas = lineas(ticket("RECIBO DE PRESTAMO Nro. 9", null, 80,
                new ReciboTicketEscPos.Row("PRESTAMO - para\nel  auto (6 cuotas)", "600.000")));
        assertTrue(lineas.stream().anyMatch(l -> l.startsWith("PRESTAMO - para el auto (6 cuotas)") && l.endsWith("600.000")),
                "el concepto tiene que quedar en una fila con el monto a la derecha: " + lineas);
    }

    @Test
    void sinObservacionNoImprimeLaLinea() {
        for (String obs : new String[]{null, "", "   \n "}) {
            List<String> lineas = lineas(ticket("RECIBO DE AGUINALDO Nro. 3", obs, 58,
                    new ReciboTicketEscPos.Row("AGUINALDO 2026 (12 meses)", "3.100.000")));
            assertTrue(lineas.stream().noneMatch(l -> l.startsWith("Obs.")),
                    "observacion [" + obs + "] no deberia imprimir la linea: " + lineas);
        }
    }

    @Test
    void ningunaLineaSuperaElAnchoDelPapel() {
        String palabraLarga = "SUPERCALIFRAGILISTICOESPIALIDOSO";   // 32 letras
        String observacion = "OBSERVACION " + palabraLarga + palabraLarga + " FIN";
        for (int ancho : new int[]{58, 80}) {
            int cols = ancho >= 80 ? 48 : 32;
            List<String> lineas = lineas(ticket("RECIBO DE PENALIZACION Nro. 123456789", observacion, ancho,
                    // Primera palabra entre el ancho de la 1ra linea (32 - monto) y el ancho total.
                    new ReciboTicketEscPos.Row("ANTICIPOSUELDOEXTRAORDINARIO extra", "1.500.000"),
                    new ReciboTicketEscPos.Row(palabraLarga + palabraLarga, "10")));
            for (String l : lineas) {
                assertTrue(l.length() <= cols, ancho + "mm: linea de " + l.length() + " > " + cols + ": [" + l + "]");
            }
            assertTrue(String.join(" ", lineas).contains("123456789"),
                    "el numero del recibo no puede perderse: " + lineas);
        }
    }

    // ===== helpers =====

    private static String ticket(String titulo, String observacion, int ancho, ReciboTicketEscPos.Row... filas) {
        return ReciboTicketEscPos.build("FRANCO SA", titulo, "JUAN PEREZ", "1234567", "2026-09-19",
                new ArrayList<>(Arrays.asList(filas)), "150.000", "CIENTO CINCUENTA MIL",
                "Recibi conforme, en concepto de vale,", observacion, ancho);
    }

    private static int indiceQueEmpieza(List<String> lineas, String prefijo) {
        for (int i = 0; i < lineas.size(); i++) if (lineas.get(i).startsWith(prefijo)) return i;
        return -1;
    }

    /**
     * Lineas de texto del payload. Los comandos que emite la libreria son todos de 3 bytes
     * (ESC M/E/-/a/3/t n, GS !/B/V n) salvo ESC 2, que es de 2.
     */
    public static List<String> lineas(String base64) {
        byte[] b = Base64.getDecoder().decode(base64);
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < b.length; i++) {
            if (b[i] == ESC || b[i] == GS) {
                i += (b[i] == ESC && i + 1 < b.length && b[i + 1] == '2') ? 1 : 2;
                continue;
            }
            if (b[i] == '\n') {
                out.add(new String(actual.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1));
                actual.reset();
                continue;
            }
            actual.write(b[i]);
        }
        if (actual.size() > 0) out.add(new String(actual.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1));
        out.removeAll(Collections.singleton(""));
        return out;
    }
}
