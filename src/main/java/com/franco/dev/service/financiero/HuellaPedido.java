package com.franco.dev.service.financiero;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * Huella de un pedido para {@link IdempotenciaService}: los campos que lo definen, en el orden en
 * que se agregan, reducidos a un SHA-256. Sirve para rechazar una clave reusada con otro contenido.
 *
 * <p>Un reintento legítimo tiene que dar <b>la misma</b> huella aunque el cliente lo haya rearmado,
 * así que cada tipo se normaliza: un monto vale lo mismo con o sin ceros a la derecha, una bandera
 * nula vale lo mismo que {@code false} y una fecha cuenta solo por su día. Lo que el cliente genera
 * al confirmar (la fecha de emisión, que es «ahora») no se agrega.</p>
 */
public final class HuellaPedido {

    private final StringBuilder sb = new StringBuilder();

    public HuellaPedido texto(String valor) {
        return agregar(valor == null ? "" : valor);
    }

    public HuellaPedido id(Long valor) {
        return agregar(valor == null ? "" : valor.toString());
    }

    public HuellaPedido numero(BigDecimal valor) {
        if (valor == null) return agregar("");
        // signum: stripTrailingZeros de un cero con escala no siempre da "0".
        return agregar(valor.signum() == 0 ? "0" : valor.stripTrailingZeros().toPlainString());
    }

    public HuellaPedido bandera(Boolean valor) {
        return agregar(Boolean.TRUE.equals(valor) ? "1" : "0");
    }

    public HuellaPedido dia(LocalDateTime valor) {
        return agregar(valor == null ? "" : valor.toLocalDate().toString());
    }

    /** Largo + valor: dos campos distintos no pueden confundirse aunque el texto traiga el separador. */
    private HuellaPedido agregar(String valor) {
        sb.append(valor.length()).append(':').append(valor).append('|');
        return this;
    }

    public String calcular() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
