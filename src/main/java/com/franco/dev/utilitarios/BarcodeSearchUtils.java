package com.franco.dev.utilitarios;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta escaneos de código de barras / QR y genera candidatos para búsqueda en BD.
 */
public final class BarcodeSearchUtils {

    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^(\\d{6,14})");
    private static final Pattern ALPHANUM_TOKEN = Pattern.compile("^([A-Z0-9][A-Z0-9\\-._]{3,31})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_TOKEN = Pattern.compile("^[A-Z0-9\\-._]{4,32}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GS1_GTIN = Pattern.compile("\\(01\\)(\\d{14})");

    private BarcodeSearchUtils() {
    }

    public static List<String> codigosParaBuscar(String texto) {
        List<String> candidatos = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return candidatos;
        }

        String trimmed = texto.trim();
        String upper = trimmed.toUpperCase();

        if (esCodigoPesable(trimmed)) {
            agregar(candidatos, trimmed);
        }

        Matcher numeric = NUMERIC_PREFIX.matcher(trimmed);
        String prefijoNumerico = null;
        if (numeric.find()) {
            prefijoNumerico = numeric.group(1);
            agregar(candidatos, prefijoNumerico);
        }

        Matcher gs1 = GS1_GTIN.matcher(trimmed);
        if (gs1.find()) {
            String gtin14 = gs1.group(1);
            agregar(candidatos, gtin14);
            if (gtin14.length() == 14 && gtin14.startsWith("0")) {
                agregar(candidatos, gtin14.substring(1));
            }
        }

        Matcher alpha = ALPHANUM_TOKEN.matcher(trimmed);
        if (alpha.find()) {
            String token = alpha.group(1);
            if (prefijoNumerico == null || !token.equalsIgnoreCase(prefijoNumerico)) {
                agregar(candidatos, token);
            }
        }

        if (!trimmed.contains(" ") && SINGLE_TOKEN.matcher(trimmed).matches()) {
            agregar(candidatos, upper);
        }

        if (trimmed.matches("\\d{6,14}")) {
            agregar(candidatos, trimmed);
        }

        return candidatos;
    }

    public static boolean esCodigoPesable(String texto) {
        if (texto == null) {
            return false;
        }
        String t = texto.trim();
        return t.length() == 13 && t.startsWith("20");
    }

    private static void agregar(List<String> lista, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        String normalizado = codigo.trim().toUpperCase();
        if (!lista.contains(normalizado)) {
            lista.add(normalizado);
        }
    }
}
