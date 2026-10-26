package com.franco.dev.service.empresarial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CifradoConfiguracionTest {

    @Test
    void idaYVueltaDevuelveLoMismo() {
        CifradoConfiguracion c = new CifradoConfiguracion("clave-maestra");
        assertEquals("sk-secreto", c.descifrar(c.cifrar("sk-secreto")));
    }

    @Test
    void dosCifradosDelMismoTextoSonDistintos() {
        CifradoConfiguracion c = new CifradoConfiguracion("clave-maestra");
        assertNotEquals(c.cifrar("sk-x"), c.cifrar("sk-x"));
    }

    @Test
    void otraMasterKeyNoDescifra() {
        String cifrado = new CifradoConfiguracion("una").cifrar("sk-x");
        assertThrows(IllegalStateException.class, () -> new CifradoConfiguracion("otra").descifrar(cifrado));
    }

    @Test
    void sinMasterKeyNoEstaDisponible() {
        assertFalse(new CifradoConfiguracion("").disponible());
        assertFalse(new CifradoConfiguracion(null).disponible());
        assertTrue(new CifradoConfiguracion("k").disponible());
    }
}
