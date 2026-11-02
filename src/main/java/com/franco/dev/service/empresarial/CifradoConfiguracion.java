package com.franco.dev.service.empresarial;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM con clave = SHA-256(master key). Se guarda Base64(iv || cifrado). */
public class CifradoConfiguracion {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec clave;

    public CifradoConfiguracion(String masterKey) {
        if (masterKey == null || masterKey.trim().isEmpty()) {
            this.clave = null;
        } else {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
                this.clave = new SecretKeySpec(hash, "AES");
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo derivar la clave de cifrado", e);
            }
        }
    }

    public boolean disponible() {
        return clave != null;
    }

    public String cifrar(String texto) {
        exigirClave();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el valor", e);
        }
    }

    public String descifrar(String base64) {
        exigirClave();
        try {
            byte[] in = Base64.getDecoder().decode(base64);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el valor (¿cambió CONFIG_MASTER_KEY?)", e);
        }
    }

    private void exigirClave() {
        if (clave == null) throw new IllegalStateException("CONFIG_MASTER_KEY no está configurada");
    }
}
