package com.franco.dev.utilitarios;

/**
 * Generación / validación de EAN-13 para códigos internos in-house.
 * Prefijo comercial interno Franco: {@value #INTERNAL_PREFIX} (subrango de 21…,
 * evitando 20… reservado para productos de balanza).
 */
public final class Ean13Utils {

    /** Prefijo de 4 dígitos + secuencia de 8 = 12 dígitos de datos. */
    public static final String INTERNAL_PREFIX = "2199";

    private static final int SEQ_DIGITS = 8;
    private static final long MAX_SEQ = 99_999_999L;

    private Ean13Utils() {
    }

    /**
     * Construye un EAN-13: {@code prefix(4) + seq(8) + checkDigit}.
     */
    public static String fromInternalSequence(long sequence) {
        if (sequence < 1 || sequence > MAX_SEQ) {
            throw new IllegalArgumentException("Secuencia EAN interna fuera de rango: " + sequence);
        }
        String body12 = INTERNAL_PREFIX + String.format("%0" + SEQ_DIGITS + "d", sequence);
        return body12 + checkDigit(body12);
    }

    /**
     * Dígito de control EAN/UPC para exactamente 12 dígitos.
     */
    public static char checkDigit(String twelveDigits) {
        if (twelveDigits == null || !twelveDigits.matches("\\d{12}")) {
            throw new IllegalArgumentException("Se requieren 12 dígitos para checksum EAN-13");
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = twelveDigits.charAt(i) - '0';
            // índices pares (desde 0) ×1, impares ×3
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int mod = sum % 10;
        return (char) ('0' + ((mod == 0) ? 0 : (10 - mod)));
    }

    public static boolean isValidEan13(String code) {
        if (code == null || !code.matches("\\d{13}")) {
            return false;
        }
        return checkDigit(code.substring(0, 12)) == code.charAt(12);
    }

    public static boolean isInternalGenerated(String code) {
        return code != null && code.matches("^" + INTERNAL_PREFIX + "\\d{9}$") && isValidEan13(code);
    }
}
