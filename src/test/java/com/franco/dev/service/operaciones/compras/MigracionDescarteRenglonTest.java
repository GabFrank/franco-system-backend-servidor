package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La migración V268.3 se lee como texto: solo aditiva, con las columnas de descarte del renglón. */
class MigracionDescarteRenglonTest {

    private static String sql() throws Exception {
        try (InputStream in = MigracionDescarteRenglonTest.class.getResourceAsStream(
                "/db/migration/V268.3__documento_recibido_item_descarte.sql")) {
            assertNotNull(in, "falta V268.3__documento_recibido_item_descarte.sql");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void contieneLoPedido() throws Exception {
        String s = sql();
        for (String esperado : new String[]{"ADD COLUMN IF NOT EXISTS descartado boolean NOT NULL DEFAULT false",
                "motivo_descarte", "descartado_por", "descartado_en", "operaciones.documento_recibido_item"}) {
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
