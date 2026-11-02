package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Borrar una nota con la recepción documental ya finalizada dejaba el pedido sin notas y con la etapa cerrada:
 * no se le podía cargar otra factura (pedido 2069 en bodega3, 2026-10-23).
 */
class NotaRecepcionEliminableTest {

    @Test
    void conLaEtapaAbiertaSeBorra() {
        assertNull(NotaRecepcionService.motivoNoEliminable(10L, ProcesoEtapaEstado.PENDIENTE));
        assertNull(NotaRecepcionService.motivoNoEliminable(10L, ProcesoEtapaEstado.EN_PROCESO));
    }

    @Test
    void sinEtapaNiPedidoSeBorra() {
        assertNull(NotaRecepcionService.motivoNoEliminable(10L, null));
        assertNull(NotaRecepcionService.motivoNoEliminable(null, ProcesoEtapaEstado.COMPLETADA));
    }

    @Test
    void conLaRecepcionDocumentalFinalizadaNoSeBorra() {
        String motivo = NotaRecepcionService.motivoNoEliminable(2069L, ProcesoEtapaEstado.COMPLETADA);
        assertNotNull(motivo);
        assertTrue(motivo.contains("2069"));
    }
}
