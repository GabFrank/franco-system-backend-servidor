package com.franco.dev.service.financiero;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La version minima configurada contra la requerida (moneda.desktop.version-minima-requerida) compara
 * solo la base X.Y.Z: una prerelease cuenta como su release (desktop#710). Antes partia por puntos y
 * leia 4.2.0-alpha.345 como 4.2.0.345.
 */
class ControlVersionMonedaTest {

    @Test
    void releases() {
        assertTrue(ControlVersionMoneda.menor("4.1.9", "4.2.0"));
        assertFalse(ControlVersionMoneda.menor("4.2.0", "4.2.0"));
        assertFalse(ControlVersionMoneda.menor("4.10.0", "4.2.0"));
    }

    @Test
    void prereleaseCuentaComoSuRelease() {
        assertFalse(ControlVersionMoneda.menor("4.2.0-alpha.345", "4.2.0"));
        assertFalse(ControlVersionMoneda.menor("4.2.0", "4.2.0-alpha.345"), "antes: 0 < 345 -> menor");
        assertFalse(ControlVersionMoneda.menor("4.2.0-alpha.1", "4.2.0-alpha.345"));
        assertTrue(ControlVersionMoneda.menor("4.1.0-alpha.999", "4.2.0"));
        assertFalse(ControlVersionMoneda.menor("4.3.0-alpha.1", "4.2.0-beta.2"));
    }
}
