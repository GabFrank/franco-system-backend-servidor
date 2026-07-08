package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DevolucionTransicionValidatorTest {

    private static final boolean CON_PROVEEDOR = true;
    private static final boolean SIN_PROVEEDOR = false;

    // --- flujo CON_PROVEEDOR feliz ---

    @Test
    void conProveedor_flujoCompletoCanje_esValido() {
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.PENDIENTE, DevolucionEstado.SEPARADO, CON_PROVEEDOR));
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.SEPARADO, DevolucionEstado.RETIRADO, CON_PROVEEDOR));
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.RETIRADO, DevolucionEstado.CANJEADO, CON_PROVEEDOR));
    }

    @Test
    void conProveedor_acreditarDesdeRetirado_esValido() {
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.RETIRADO, DevolucionEstado.ACREDITADO, CON_PROVEEDOR));
    }

    // --- flujo SIN_PROVEEDOR feliz ---

    @Test
    void sinProveedor_flujoDescarte_esValido() {
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.PENDIENTE, DevolucionEstado.SEPARADO, SIN_PROVEEDOR));
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.SEPARADO, DevolucionEstado.DESCARTADO, SIN_PROVEEDOR));
    }

    // --- reglas por tipo ---

    @Test
    void sinProveedor_noPuedeRetirar() {
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.SEPARADO, DevolucionEstado.RETIRADO, SIN_PROVEEDOR));
    }

    @Test
    void conProveedor_noPuedeDescartar() {
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.SEPARADO, DevolucionEstado.DESCARTADO, CON_PROVEEDOR));
    }

    @Test
    void sinProveedor_noPuedeCanjearNiAcreditar() {
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.RETIRADO, DevolucionEstado.CANJEADO, SIN_PROVEEDOR));
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.RETIRADO, DevolucionEstado.ACREDITADO, SIN_PROVEEDOR));
    }

    // --- saltos invalidos ---

    @Test
    void noSePuedeSaltarDeSeparadoACanjeado() {
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.SEPARADO, DevolucionEstado.CANJEADO, CON_PROVEEDOR));
    }

    @Test
    void separadoSoloDesdePendiente() {
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.PENDIENTE, DevolucionEstado.SEPARADO, CON_PROVEEDOR));
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.RETIRADO, DevolucionEstado.SEPARADO, CON_PROVEEDOR));
    }

    // --- cancelacion ---

    @Test
    void cancelarDesdePendienteOSeparado_esValido() {
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.PENDIENTE, DevolucionEstado.CANCELADA, CON_PROVEEDOR));
        assertNull(DevolucionTransicionValidator.validar(DevolucionEstado.SEPARADO, DevolucionEstado.CANCELADA, SIN_PROVEEDOR));
    }

    @Test
    void noCancelarDesdeRetirado() {
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.RETIRADO, DevolucionEstado.CANCELADA, CON_PROVEEDOR));
    }

    // --- estados terminales ---

    @Test
    void estadosTerminales_noAdmitenTransicion() {
        for (DevolucionEstado terminal : new DevolucionEstado[]{
                DevolucionEstado.CANJEADO, DevolucionEstado.ACREDITADO,
                DevolucionEstado.DESCARTADO, DevolucionEstado.CANCELADA}) {
            assertTrue(DevolucionTransicionValidator.esTerminal(terminal));
            assertNotNull(DevolucionTransicionValidator.validar(terminal, DevolucionEstado.SEPARADO, CON_PROVEEDOR));
        }
        assertFalse(DevolucionTransicionValidator.esTerminal(DevolucionEstado.PENDIENTE));
        assertFalse(DevolucionTransicionValidator.esTerminal(DevolucionEstado.SEPARADO));
        assertFalse(DevolucionTransicionValidator.esTerminal(DevolucionEstado.RETIRADO));
    }

    @Test
    void nullsRetornanError() {
        assertNotNull(DevolucionTransicionValidator.validar(null, DevolucionEstado.SEPARADO, CON_PROVEEDOR));
        assertNotNull(DevolucionTransicionValidator.validar(DevolucionEstado.PENDIENTE, null, CON_PROVEEDOR));
    }
}
