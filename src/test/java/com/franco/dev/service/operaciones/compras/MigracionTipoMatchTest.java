package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #590: documento_recibido_item.tipo_match era VARCHAR(20) y APRENDIDO_DESCRIPCION tiene 21 caracteres.
 * La migración V269.3 agranda la columna; cada TipoMatch tiene que entrar en ella.
 */
class MigracionTipoMatchTest {

    private static String sql() throws Exception {
        try (InputStream in = MigracionTipoMatchTest.class.getResourceAsStream(
                "/db/migration/V269.3__documento_recibido_item_tipo_match_largo.sql")) {
            assertNotNull(in, "falta V269.3__documento_recibido_item_tipo_match_largo.sql");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int largoColumna(String s) {
        Matcher m = Pattern.compile("ALTER COLUMN tipo_match TYPE VARCHAR\\((\\d+)\\)", Pattern.CASE_INSENSITIVE)
                .matcher(s);
        assertTrue(m.find(), "debe cambiar el tipo de tipo_match a VARCHAR(n)");
        return Integer.parseInt(m.group(1));
    }

    @Test
    void cadaTipoMatchEntraEnLaColumna() throws Exception {
        int largo = largoColumna(sql());
        for (ComprasMobileService.TipoMatch t : ComprasMobileService.TipoMatch.values()) {
            assertTrue(t.name().length() <= largo, t.name() + " no entra en VARCHAR(" + largo + ")");
        }
    }

    @Test
    void soloAgrandaLaColumna() throws Exception {
        String s = sql();
        assertTrue(s.contains("operaciones.documento_recibido_item"));
        assertFalse(s.contains("DROP"));
        assertFalse(s.contains("RENAME"));
        assertFalse(s.contains("UPDATE"));
    }
}
