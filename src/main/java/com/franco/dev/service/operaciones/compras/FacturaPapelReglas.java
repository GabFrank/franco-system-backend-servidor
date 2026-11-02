package com.franco.dev.service.operaciones.compras;

import com.franco.dev.utilitarios.CalcularVerificadorRuc;

import java.util.List;

/** Validaciones puras de los datos leídos de una factura en papel. */
public final class FacturaPapelReglas {

    private FacturaPapelReglas() {
    }

    /** RUC con DV ("80012345-6"): el DV tiene que coincidir con el módulo 11 de la SET. */
    public static boolean rucValido(String rucConDv) {
        if (rucConDv == null) return false;
        String s = rucConDv.trim();
        int i = s.lastIndexOf('-');
        if (i <= 0 || i != s.length() - 2) return false;
        String ruc = s.substring(0, i);
        char dv = s.charAt(i + 1);
        if (!ruc.matches("\\d+") || dv < '0' || dv > '9') return false;
        Integer esperado = CalcularVerificadorRuc.getDigitoVerificador(ruc, 11);
        return esperado != null && esperado == dv - '0';
    }

    /** El RUC sin el guion ni el DV. */
    public static String sinDv(String ruc) {
        if (ruc == null) return null;
        String s = ruc.trim();
        int i = s.indexOf('-');
        return i < 0 ? s : s.substring(0, i);
    }

    /** Partes ya rellenadas: 3, 3 y 7 dígitos. */
    public static boolean numeroValido(String est, String pto, String num) {
        return est != null && pto != null && num != null
                && est.matches("\\d{3}") && pto.matches("\\d{3}") && num.matches("\\d{7}");
    }

    public static boolean timbradoValido(Integer timbrado) {
        return timbrado != null && String.valueOf(timbrado).matches("\\d{8}");
    }

    /** Diferencia permitida: el mayor entre 1 % del total y 1 Gs por ítem. */
    public static boolean totalesCuadran(double total, List<Double> totalesItems) {
        if (totalesItems == null || totalesItems.isEmpty()) return false;
        double suma = 0;
        for (Double t : totalesItems) suma += t == null ? 0 : t;
        double tolerancia = Math.max(0.01 * total, 1.0 * totalesItems.size());
        return Math.abs(total - suma) <= tolerancia;
    }

    /** Ceros a la izquierda; null si es vacío, no son solo dígitos o excede n. */
    public static String rellenar(String s, int n) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || !t.matches("\\d+") || t.length() > n) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = t.length(); i < n; i++) sb.append('0');
        return sb.append(t).toString();
    }
}
