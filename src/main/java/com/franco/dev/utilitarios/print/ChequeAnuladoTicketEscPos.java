package com.franco.dev.utilitarios.print;

import com.franco.dev.utilitarios.print.escpos.EscPos;
import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.franco.dev.utilitarios.print.escpos.Style;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static com.franco.dev.utilitarios.print.ReciboTicketEscPos.dosColumnas;
import static com.franco.dev.utilitarios.print.ReciboTicketEscPos.linea;
import static com.franco.dev.utilitarios.print.ReciboTicketEscPos.na;
import static com.franco.dev.utilitarios.print.ReciboTicketEscPos.repeat;
import static com.franco.dev.utilitarios.print.ReciboTicketEscPos.textoEnUnaLinea;
import static com.franco.dev.utilitarios.print.ReciboTicketEscPos.wrapAncho;

/**
 * Comprobante térmico «CHEQUE ANULADO», para presillar al cheque (desktop #807). Devuelve el base64 de los bytes
 * ESC/POS para la impresora local del desktop ({@code printLocal}); no imprime. Sin acentos, como los recibos de RRHH.
 */
public final class ChequeAnuladoTicketEscPos {

    private ChequeAnuladoTicketEscPos() {}

    /** Los textos ya formateados. Un campo null sale como «sin dato» (cheques anulados antes de #807). */
    public static class Datos {
        public String empresa;
        public String numero;
        public String banco;
        public String cuenta;
        public String beneficiario;
        public String monto;
        public String fechaEmision;
        public String fechaPago;
        public String motivo;
        public String observacion;
        public String fechaAnulacion;
        public String usuarioAnulacion;
        /** «Movimiento bancario revertido» / «Reserva liberada»; null no imprime la línea. */
        public String efecto;
    }

    /** @param anchoMm 58 o 80 (32 / 48 columnas). */
    public static String build(Datos d, Integer anchoMm) {
        int cols = (anchoMm != null && anchoMm >= 80) ? 48 : 32;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            EscPos esc = new EscPos(baos);
            Style center = new Style().setJustification(EscPosConst.Justification.Center);
            Style centerBold = new Style().setJustification(EscPosConst.Justification.Center).setBold(true);
            Style bold = new Style().setBold(true);

            if (d.empresa != null && !d.empresa.isEmpty()) esc.writeLF(centerBold, na(d.empresa));
            esc.writeLF(centerBold, "CHEQUE ANULADO");
            esc.writeLF(linea(cols));
            esc.writeLF(bold, dosColumnas("Cheque N.:", valor(d.numero), cols));
            campo(esc, "Banco: ", d.banco, cols);
            campo(esc, "Cuenta: ", d.cuenta, cols);
            campo(esc, "Beneficiario: ", d.beneficiario, cols);
            esc.writeLF(bold, dosColumnas("Monto:", valor(d.monto), cols));
            esc.writeLF(dosColumnas("Emision:", valor(d.fechaEmision), cols));
            esc.writeLF(dosColumnas("Pago:", valor(d.fechaPago), cols));
            esc.writeLF(linea(cols));
            campo(esc, "Motivo: ", d.motivo, cols);
            String obs = textoEnUnaLinea(d.observacion);
            if (obs != null) campo(esc, "Obs.: ", obs, cols);
            esc.writeLF(dosColumnas("Anulado:", valor(d.fechaAnulacion), cols));
            campo(esc, "Por: ", d.usuarioAnulacion, cols);
            if (d.efecto != null) campo(esc, "", d.efecto, cols);
            esc.feed(3);
            esc.writeLF(center, repeat("-", Math.min(cols, 28)));
            esc.writeLF(center, "Firma responsable");
            esc.feed(4);
            esc.cut(EscPos.CutMode.FULL);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Error generando el ticket del cheque anulado: " + e.getMessage(), e);
        }
    }

    /** Rótulo + valor envuelto por palabras: un beneficiario largo no se corta donde caiga. */
    private static void campo(EscPos esc, String rotulo, String v, int cols) throws Exception {
        for (String l : wrapAncho(na(rotulo + valor(textoEnUnaLinea(v))), cols, cols)) esc.writeLF(l);
    }

    private static String valor(String v) {
        return v == null || v.trim().isEmpty() ? "sin dato" : v.trim();
    }
}
