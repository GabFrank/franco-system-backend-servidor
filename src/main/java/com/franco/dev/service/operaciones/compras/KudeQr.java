package com.franco.dev.service.operaciones.compras;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Lee lo que escanea el celular sobre un KuDE: la URL del QR de SIFEN o el CDC suelto (los 44 dígitos impresos
 * debajo del QR, con o sin espacios).
 *
 * El CDC ya dice de quién es la factura sin consultar a SIFEN: tipo de documento, RUC del emisor,
 * establecimiento, punto, número y fecha. Por eso alcanza para elegir el pedido del proveedor antes de pedir la
 * factura completa.
 *
 * Nunca tira excepción por un código que no es un KuDE: devuelve un resultado con el motivo (ver PATRONES de la
 * PWA, §5).
 */
public final class KudeQr {

    public enum Motivo { VACIO, NO_ES_KUDE, CDC_INVALIDO, DIGITO_VERIFICADOR }

    private final boolean ok;
    private final Motivo motivo;
    private final String mensaje;
    private final String cdc;
    private final Map<String, String> parametros;

    private KudeQr(boolean ok, Motivo motivo, String mensaje, String cdc, Map<String, String> parametros) {
        this.ok = ok;
        this.motivo = motivo;
        this.mensaje = mensaje;
        this.cdc = cdc;
        this.parametros = parametros;
    }

    public static KudeQr leer(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return rechazo(Motivo.VACIO, "No llegó ningún código.");
        }
        String limpio = texto.trim();
        Map<String, String> params = new HashMap<>();
        String candidato;
        boolean esUrlDeKude = false;
        int interrogacion = limpio.indexOf('?');
        if (interrogacion >= 0 && limpio.toLowerCase().contains("id=")) {
            params = parametrosDe(limpio.substring(interrogacion + 1));
            candidato = params.get("id");
            if (candidato == null) {
                return rechazo(Motivo.NO_ES_KUDE, "Ese QR no es el de una factura electrónica.");
            }
            esUrlDeKude = true;
        } else {
            candidato = limpio;
        }
        String digitos = candidato.replaceAll("[\\s.\\-]", "");
        if (!digitos.matches("\\d{44}")) {
            return rechazo(esUrlDeKude ? Motivo.CDC_INVALIDO : Motivo.NO_ES_KUDE,
                    "Ese código no es el QR ni el CDC de una factura electrónica.");
        }
        if (digitoVerificador(digitos.substring(0, 43)) != digitos.charAt(43) - '0') {
            return rechazo(Motivo.DIGITO_VERIFICADOR,
                    "El CDC no es válido (dígito verificador). Si lo escribiste a mano, revisalo.");
        }
        return new KudeQr(true, null, null, digitos, params);
    }

    private static KudeQr rechazo(Motivo motivo, String mensaje) {
        return new KudeQr(false, motivo, mensaje, null, new HashMap<>());
    }

    /** Parámetros del QR, con la clave en minúsculas. */
    private static Map<String, String> parametrosDe(String query) {
        Map<String, String> params = new HashMap<>();
        for (String par : query.split("&")) {
            int igual = par.indexOf('=');
            if (igual <= 0) continue;
            String clave = par.substring(0, igual).replace("amp;", "").trim().toLowerCase();
            String valor = URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8).trim();
            params.put(clave, valor);
        }
        return params;
    }

    /**
     * Módulo 11 de SIFEN (el mismo del RUC): pesos 2..11 de derecha a izquierda, cíclicos. Resto 0 o 1 da 0.
     */
    public static int digitoVerificador(String numero) {
        int peso = 2;
        int suma = 0;
        for (int i = numero.length() - 1; i >= 0; i--) {
            suma += (numero.charAt(i) - '0') * peso;
            peso = peso == 11 ? 2 : peso + 1;
        }
        int resto = suma % 11;
        return resto > 1 ? 11 - resto : 0;
    }

    // ------------------------------------------------------------- Lo que dice el CDC

    public boolean isOk() { return ok; }
    public Motivo getMotivo() { return motivo; }
    public String getMensaje() { return mensaje; }
    public String getCdc() { return cdc; }

    /** 1 factura, 4 autofactura, 5 nota de crédito, 6 nota de débito, 7 nota de remisión. */
    public int getTipoDocumento() { return Integer.parseInt(cdc.substring(0, 2)); }

    /** RUC del emisor sin ceros a la izquierda y sin DV. */
    public String getRucEmisor() { return cdc.substring(2, 10).replaceFirst("^0+(?=.)", ""); }

    public String getDvEmisor() { return cdc.substring(10, 11); }

    public String getEstablecimiento() { return cdc.substring(11, 14); }

    public String getPuntoExpedicion() { return cdc.substring(14, 17); }

    public String getNumero() { return cdc.substring(17, 24); }

    public LocalDate getFechaEmision() {
        try {
            return LocalDate.parse(cdc.substring(25, 33), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** RUC del receptor según el QR (no está en el CDC). Null si se escaneó el CDC suelto. */
    public String getRucReceptor() { return parametros.get("drucrec"); }

    public static String descripcionTipo(Integer tipo) {
        if (tipo == null) return "Documento electrónico";
        switch (tipo) {
            case 1: return "Factura electrónica";
            case 4: return "Autofactura electrónica";
            case 5: return "Nota de crédito electrónica";
            case 6: return "Nota de débito electrónica";
            case 7: return "Nota de remisión electrónica";
            default: return "Documento electrónico";
        }
    }
}
