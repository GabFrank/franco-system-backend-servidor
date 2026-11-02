package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.rrhh.LiquidacionFinalService;
import com.franco.dev.service.rrhh.RrhhAlcanceService;
import com.franco.dev.service.rrhh.RrhhSecurityFalso;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Eliminar un ítem programado del finiquito anula el programado: el rol acotado no puede. */
class EliminarItemProgramadoFiniquitoRolTest {

    private LiquidacionFinalService service;

    private LiquidacionFinalGraphQL resolver(String rol, boolean acotado) {
        service = mock(LiquidacionFinalService.class);
        when(service.esItemProgramado(287L)).thenReturn(true);
        when(service.eliminarItem(anyLong())).thenReturn(true);
        RrhhAlcanceService alcance = mock(RrhhAlcanceService.class);
        when(alcance.liquidaAcotado()).thenReturn(acotado);
        LiquidacionFinalGraphQL r = new LiquidacionFinalGraphQL();
        ReflectionTestUtils.setField(r, "service", service);
        ReflectionTestUtils.setField(r, "seg", new RrhhSecurityFalso(rol));
        ReflectionTestUtils.setField(r, "alcance", alcance);
        return r;
    }

    @Test
    void acotado_noEliminaUnItemProgramado() {
        LiquidacionFinalGraphQL r = resolver("RRHH SUCURSAL", true);

        assertThrows(RuntimeException.class, () -> r.eliminarItemLiquidacionFinal(287L));
        verify(service, never()).eliminarItem(anyLong());
    }

    @Test
    void acotado_sigueEliminandoUnItemComun() {
        LiquidacionFinalGraphQL r = resolver("RRHH SUCURSAL", true);

        assertTrue(r.eliminarItemLiquidacionFinal(286L));
    }

    @Test
    void liquidar_eliminaUnItemProgramado() {
        LiquidacionFinalGraphQL r = resolver("RRHH LIQUIDAR", false);

        assertTrue(r.eliminarItemLiquidacionFinal(287L));
    }
}
