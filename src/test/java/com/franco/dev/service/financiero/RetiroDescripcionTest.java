package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Retiro;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetiroDescripcionTest {

    @Test
    void lleva_la_caja_de_salida_del_pdv() {
        Retiro r = new Retiro();
        r.setId(123L);
        r.setCajaSalidaId(456L);
        assertEquals("Retiro #123 - SUCURSAL X - Caja #456", RetiroDescripcion.de(r, "SUCURSAL X"));
    }

    @Test
    void sin_caja_de_salida_la_omite() {
        Retiro r = new Retiro();
        r.setId(123L);
        assertEquals("Retiro #123 - SUCURSAL X", RetiroDescripcion.de(r, "SUCURSAL X"));
    }
}
