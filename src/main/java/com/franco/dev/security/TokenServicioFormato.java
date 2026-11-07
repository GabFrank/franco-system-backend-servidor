package com.franco.dev.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Formato del token de servicio (issue #579): {@code frcsvc_} + 32 bytes aleatorios en base64url.
 *
 * Un JWT empieza siempre con {@code eyJ}, asi que el prefijo nunca se confunde con un token de sesion.
 * Con 256 bits aleatorios alcanza un SHA-256 simple para guardarlo: no hay diccionario que probar.
 */
public final class TokenServicioFormato {

    public static final String PREFIJO = "frcsvc_";
    /** Caracteres del token que se guardan en claro para reconocerlo. */
    public static final int LARGO_PREFIJO_VISIBLE = 12;

    private static final int BYTES_ALEATORIOS = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenServicioFormato() {
    }

    public static boolean esTokenDeServicio(String token) {
        return token != null && token.startsWith(PREFIJO);
    }

    public static String generar() {
        byte[] b = new byte[BYTES_ALEATORIOS];
        RANDOM.nextBytes(b);
        return PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String prefijoVisible(String token) {
        return token.substring(0, Math.min(LARGO_PREFIJO_VISIBLE, token.length()));
    }

    /** SHA-256 en hex minuscula (64 caracteres). */
    public static String hash(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Clave aleatoria para el usuario de servicio: cierra su login por /login (nadie la conoce). */
    public static String claveAleatoria() {
        byte[] b = new byte[36];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b).toUpperCase();
    }
}
