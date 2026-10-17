package com.franco.dev.graphql.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintVioladaTest {

    @Test
    void loEncuentraEnElMensajeDeAfuera() {
        assertTrue(ConstraintViolada.en(new RuntimeException("constraint [proveedor_un]"), "proveedor_un"));
    }

    @Test
    void loEncuentraEnUnaCausaAnidada() {
        RuntimeException raiz = new RuntimeException("duplicate key value violates unique constraint \"proveedor_un\"");
        RuntimeException ex = new RuntimeException("Could not commit JPA transaction", new RuntimeException("Error while committing", raiz));
        assertTrue(ConstraintViolada.en(ex, "proveedor_un"));
    }

    @Test
    void noLoEncuentraSiNoEsta() {
        assertFalse(ConstraintViolada.en(new RuntimeException("connection refused"), "proveedor_un"));
    }

    @Test
    void noConfundeUnNombreQueEstaDentroDeOtro() {
        RuntimeException ex = new RuntimeException("unique constraint \"caja_categoria_observacion_unique\"");
        assertFalse(ConstraintViolada.en(ex, "categoria_observacion_unique"));
        assertTrue(ConstraintViolada.en(ex, "caja_categoria_observacion_unique"));
        assertFalse(ConstraintViolada.en(new RuntimeException("constraint [proveedor_servicio_un]"), "proveedor_un"));
    }

    @Test
    void mensajesNullYSinExcepcion() {
        assertFalse(ConstraintViolada.en(new RuntimeException((String) null), "proveedor_un"));
        assertTrue(ConstraintViolada.en(new RuntimeException((String) null, new RuntimeException("[proveedor_un]")), "proveedor_un"));
        assertFalse(ConstraintViolada.en(null, "proveedor_un"));
    }

    @Test
    void terminaConUnCicloDeCausas() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertFalse(ConstraintViolada.en(a, "proveedor_un"));
    }
}
