package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DevolucionEdicionValidatorTest {

    @Test
    void edicion_soloEnPendiente() {
        assertNull(DevolucionEdicionValidator.validarEdicion(DevolucionEstado.PENDIENTE));
        // Una devolucion que todavia no se guardo no tiene estado.
        assertNull(DevolucionEdicionValidator.validarEdicion(null));
        for (DevolucionEstado estado : DevolucionEstado.values()) {
            if (estado == DevolucionEstado.PENDIENTE) continue;
            assertNotNull(DevolucionEdicionValidator.validarEdicion(estado), "debe rechazar en " + estado);
        }
    }

    @Test
    void datosDeCanje_soloEnRetirado() {
        for (DevolucionEstado estado : DevolucionEstado.values()) {
            assertEquals(estado == DevolucionEstado.RETIRADO,
                    DevolucionEdicionValidator.admiteDatosDeCanje(estado), "estado " + estado);
        }
    }

    @Test
    void reingreso_entreCeroYLoDevuelto() {
        assertNull(DevolucionEdicionValidator.validarReingreso(2.0, null, "X"));
        assertNull(DevolucionEdicionValidator.validarReingreso(2.0, 0.0, "X"));
        assertNull(DevolucionEdicionValidator.validarReingreso(2.0, 1.5, "X"));
        assertNull(DevolucionEdicionValidator.validarReingreso(2.0, 2.0, "X"));
    }

    @Test
    void reingreso_mayorALoDevuelto_rechaza() {
        assertNotNull(DevolucionEdicionValidator.validarReingreso(2.0, 5.0, "X"));
        assertNotNull(DevolucionEdicionValidator.validarReingreso(null, 1.0, "X"));
    }

    @Test
    void reingreso_negativo_rechaza() {
        assertNotNull(DevolucionEdicionValidator.validarReingreso(2.0, -1.0, "X"));
    }
}
