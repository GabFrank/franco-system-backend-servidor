package com.franco.dev.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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

    /**
     * Autoridad que lleva un request autenticado con token de servicio. No es un rol de negocio
     * (los roles se leen de la base): solo marca la sesion para lo que un bot no puede hacer.
     */
    public static final String AUTORIDAD_SESION = "SESION_TOKEN_SERVICIO";

    private static final int BYTES_ALEATORIOS = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenServicioFormato() {
    }

    /** true si el request actual entro con un token de servicio. */
    public static boolean sesionEsDeServicio() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (AUTORIDAD_SESION.equals(a.getAuthority())) return true;
        }
        return false;
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
