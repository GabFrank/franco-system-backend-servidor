package com.franco.dev.service.impresion;

import java.util.ArrayList;
import java.util.List;

/**
 * Armado de texto del comprobante de retiro para impresora termica.
 *
 * Es codigo puro (sin impresora ni base de datos) para poder testear el layout
 * en 58mm (32 columnas) y 80mm (48 columnas): lo que se rompe en un ticket es
 * el ancho, y eso no se puede verificar contra una impresora real en CI.
 */
public final class TicketRetiroLayout {

    /** Caracteres por linea segun el ancho fisico del papel. */
    public static final int COLUMNAS_58MM = 32;
    public static final int COLUMNAS_80MM = 48;
    private static final int ANCHO_58MM = 58;

    private TicketRetiroLayout() {
    }

    /** 58mm => 32 columnas; 80mm => 48. Null asume 58. */
    public static int columnasPara(Integer anchoMm) {
        if (anchoMm == null) return COLUMNAS_58MM;
        return anchoMm <= ANCHO_58MM ? COLUMNAS_58MM : COLUMNAS_80MM;
    }

    /**
     * Un item ocupa dos o tres lineas: descripcion, luego codigo + presentacion a
     * la izquierda y la cantidad a la derecha, y lote/vencimiento solo si existen.
     */
    public static List<String> lineasDeItem(String descripcion, String codigo, String factor,
                                            String cantidad, String lote, String vencimiento,
                                            int columnas) {
        List<String> lineas = new ArrayList<>();
        lineas.add(recortar(descripcion, columnas));
        String izquierda = ((codigo != null ? codigo : "") + " " + (factor != null ? factor : "")).trim();
        lineas.add(columnaDoble(izquierda, "[ ] " + cantidad, columnas));

        boolean hayLote = lote != null && !lote.trim().isEmpty();
        boolean hayVto = vencimiento != null && !vencimiento.trim().isEmpty();
        if (hayLote || hayVto) {
            lineas.add(columnaDoble(
                    hayLote ? "Lote " + lote : "",
                    hayVto ? "Vto " + vencimiento : "",
                    columnas));
        }
        return lineas;
    }

    /**
     * Izquierda y derecha en la misma linea. Si no entran, cede la izquierda:
     * la cantidad nunca se trunca, es el dato que el proveedor firma.
     */
    public static String columnaDoble(String izquierda, String derecha, int columnas) {
        String izq = izquierda != null ? izquierda.trim() : "";
        String der = derecha != null ? derecha.trim() : "";
        // Sin columna derecha no hay nada contra que alinear: no rellenar al final.
        if (der.isEmpty()) return recortar(izq, columnas);
        int espacios = columnas - izq.length() - der.length();
        if (espacios < 1) {
            izq = recortar(izq, Math.max(0, columnas - der.length() - 1));
            espacios = columnas - izq.length() - der.length();
        }
        return izq + repetir(" ", Math.max(espacios, 0)) + der;
    }

    /** Centra rellenando con espacios (la impresora recibe texto ya alineado). */
    public static String centrar(String texto, int columnas) {
        String limpio = recortar(texto, columnas);
        int sobra = columnas - limpio.length();
        return repetir(" ", sobra / 2) + limpio;
    }

    public static String recortar(String texto, int max) {
        if (texto == null) return "";
        String limpio = texto.trim();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    public static String repetir(String s, int veces) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < veces; i++) sb.append(s);
        return sb.toString();
    }

    /** Sin decimales sobrantes: "10", no "10.0". */
    public static String formatearCantidad(Double cantidad) {
        if (cantidad == null) return "0";
        if (cantidad == Math.rint(cantidad)) return String.valueOf((long) (double) cantidad);
        return String.valueOf(cantidad);
    }
}
