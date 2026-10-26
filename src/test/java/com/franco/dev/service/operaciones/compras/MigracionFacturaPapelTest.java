package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La migración V256.3 se lee como texto: solo aditiva, con lo que pide el spec. */
class MigracionFacturaPapelTest {

    private static String sql() throws Exception {
        try (InputStream in = MigracionFacturaPapelTest.class.getResourceAsStream(
                "/db/migration/V256.3__compras_factura_papel_ia.sql")) {
            assertNotNull(in, "falta V256.3__compras_factura_papel_ia.sql");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void contieneLoPedido() throws Exception {
        String s = sql();
        for (String esperado : new String[]{"ALTER COLUMN cdc DROP NOT NULL", "uk_documento_recibido_numero",
                "documento_recibido_foto", "configuracion_sistema", "'gpt-4o'"}) {
            assertTrue(s.contains(esperado), "debe contener " + esperado);
        }
    }

    @Test
    void esSoloAditiva() throws Exception {
        String s = sql();
        assertFalse(s.contains("DROP TABLE"));
        assertFalse(s.contains("DROP COLUMN"));
        assertFalse(s.contains("RENAME"));
        assertFalse(Pattern.compile("ALTER COLUMN .* TYPE").matcher(s).find());
    }
}
