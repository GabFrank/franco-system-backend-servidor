package com.franco.dev.domain.financiero.enums;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El CI no corre Flyway: si el enum gana un valor y el CHECK de la base no, la anulación con ese motivo revienta
 * recién en el servidor. Compara el enum con el CHECK de V289.1 (o de la migración que lo redefina).
 */
class MotivoAnulacionChequeCheckTest {

    @Test
    void el_check_de_la_migracion_tiene_los_mismos_valores_que_el_enum() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(
                "/db/migration/V289.1__financiero_cheque_anulacion_motivo.sql")) {
            assertNotNull(in, "no se encontró V289.1 en el classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = Pattern.compile("motivo_anulacion_tipo IN \\(([^)]*)\\)").matcher(sql);
        assertTrue(m.find(), "no se encontró el CHECK en V289.1");
        Set<String> enCheck = new TreeSet<>();
        Matcher v = Pattern.compile("'([A-Z_]+)'").matcher(m.group(1));
        while (v.find()) enCheck.add(v.group(1));

        Set<String> enEnum = Arrays.stream(MotivoAnulacionCheque.values()).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(enEnum, enCheck, "un valor nuevo del enum exige una migración que redefina el CHECK");
    }
}
