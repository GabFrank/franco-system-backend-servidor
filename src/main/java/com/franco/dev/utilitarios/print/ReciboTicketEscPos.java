package com.franco.dev.utilitarios.print;

import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Arma el ticket ESC/POS de un recibo firmable de RRHH (vale/penalizacion/
 * aguinaldo/prestamo/bono/finiquito/liquidacion) y lo devuelve como base64 de los
 * bytes crudos, listos para mandar a la impresora termica local via `lp -o raw`
 * (electron print-local). No imprime: solo genera el payload.
 *
 * Se quitan los acentos para evitar caracteres raros por codepage en termicas
 * genericas (ASCII puro imprime consistente en cualquier cola RAW).
 */
public final class ReciboTicketEscPos {

    private ReciboTicketEscPos() {}

    /** Una fila concepto/monto del ticket. */
    public static class Row {
        public final String concepto;
        public final String monto;
        public Row(String concepto, String monto) { this.concepto = concepto; this.monto = monto; }
    }

    /**
     * @param anchoMm 58 o 80. Define las columnas (32 / 48).
     * @return base64 de los bytes ESC/POS.
     */
    public static String build(String empresa, String titulo, String funcionario, String documento,
                               String fecha, List<Row> filas, String totalGs, String totalEnLetras,
                               String clausula, Integer anchoMm) {
        int cols = (anchoMm != null && anchoMm >= 80) ? 48 : 32;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            EscPos esc = new EscPos(baos);
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style centerBold = new Style().setJustification(EscPosConst.Justification.Center).setBold(true);
            Style bold = new Style().setBold(true);

            esc.writeLF(centerBold, na(empresa));
            esc.writeLF(centerBold, na(titulo));
            esc.writeLF(linea(cols));
            esc.writeLF(na("Funcionario: " + nz(funcionario)));
            esc.writeLF(na("C.I.: " + nz(documento)));
            esc.writeLF(na("Fecha: " + nz(fecha)));
            esc.writeLF(linea(cols));
            if (filas != null) {
                for (Row r : filas) {
                    for (String l : filaConcepto(r.concepto, r.monto, cols)) esc.writeLF(na(l));
                }
            }
            esc.writeLF(linea(cols));
            esc.writeLF(bold, dosColumnas("TOTAL", "Gs " + nz(totalGs), cols));
            esc.feed(1);
            for (String l : wrap(na(nz(clausula) + " " + nz(totalEnLetras) + "."), cols)) {
                esc.writeLF(l);
            }
            esc.feed(3);
            esc.writeLF(center, repeat("-", Math.min(cols, 28)));
            esc.writeLF(center, "Firma / C.I.");
            esc.feed(4);
            esc.cut(EscPos.CutMode.FULL);

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Error generando ticket ESC/POS: " + e.getMessage(), e);
        }
    }

    // ===== helpers de formato =====

    /**
     * Fila concepto/monto SIN truncar: el concepto se envuelve a varias lineas.
     * El monto va alineado a la derecha en la primera linea; las lineas de
     * continuacion muestran el resto del concepto (izquierda).
     */
    private static List<String> filaConcepto(String concepto, String monto, int cols) {
        List<String> out = new ArrayList<>();
        concepto = nz(concepto).trim();
        monto = nz(monto).trim();
        int anchoMonto = monto.length() + 1;            // + separacion minima
        int maxPrimera = cols - anchoMonto;             // ancho concepto en la 1ra linea
        if (maxPrimera < 1) maxPrimera = 1;
        // Envolver el concepto: 1ra linea limitada por el monto, resto a ancho pleno.
        List<String> lineas = wrapAncho(concepto, maxPrimera, cols);
        // 1ra linea: concepto + padding + monto.
        String prim = lineas.isEmpty() ? "" : lineas.get(0);
        int pad = cols - prim.length() - monto.length();
        if (pad < 1) pad = 1;
        out.add(prim + repeat(" ", pad) + monto);
        // Continuacion del concepto (sin monto).
        for (int i = 1; i < lineas.size(); i++) out.add(lineas.get(i));
        return out;
    }

    /** Word-wrap con ancho distinto para la 1ra linea (`maxPrimera`) y el resto (`cols`). */
    private static List<String> wrapAncho(String texto, int maxPrimera, int cols) {
        List<String> out = new ArrayList<>();
        if (texto == null || texto.isEmpty()) { out.add(""); return out; }
        StringBuilder linea = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            // Palabra sola mas larga que el ancho disponible: cortarla en pedazos.
            while (palabra.length() > cols) {
                int limite = out.isEmpty() ? maxPrimera : cols;
                if (linea.length() > 0) { out.add(linea.toString()); linea = new StringBuilder(); }
                out.add(palabra.substring(0, limite));
                palabra = palabra.substring(limite);
            }
            int ancho = out.isEmpty() ? maxPrimera : cols;
            if (linea.length() > 0 && linea.length() + 1 + palabra.length() > ancho) {
                out.add(linea.toString());
                linea = new StringBuilder();
            }
            if (linea.length() > 0) linea.append(" ");
            linea.append(palabra);
        }
        if (linea.length() > 0) out.add(linea.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    /** left + espacios + right, ajustado a `cols`; trunca el concepto si no entra. */
    private static String dosColumnas(String left, String right, int cols) {
        left = nz(left); right = nz(right);
        int max = cols - right.length() - 1;
        if (max < 1) max = 1;
        if (left.length() > max) left = left.substring(0, max);
        int pad = cols - left.length() - right.length();
        if (pad < 1) pad = 1;
        return left + repeat(" ", pad) + right;
    }

    /** Word-wrap simple a `cols` columnas. */
    private static List<String> wrap(String texto, int cols) {
        List<String> out = new ArrayList<>();
        if (texto == null || texto.isEmpty()) { out.add(""); return out; }
        StringBuilder linea = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            if (linea.length() > 0 && linea.length() + 1 + palabra.length() > cols) {
                out.add(linea.toString());
                linea = new StringBuilder();
            }
            if (linea.length() > 0) linea.append(" ");
            linea.append(palabra);
        }
        if (linea.length() > 0) out.add(linea.toString());
        return out;
    }

    private static String linea(int cols) { return repeat("-", cols); }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    /** Quita acentos/diacriticos (ASCII para termica RAW). */
    private static String na(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace("Ñ", "N").replace("ñ", "n");
    }

    private static String nz(String s) { return s != null ? s : ""; }
}
