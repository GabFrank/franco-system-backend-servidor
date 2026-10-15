package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * El ciclo nunca propaga una excepcion, una sucursal que falla no detiene a las demas, y un ciclo
 * largo se corta: lo que falte se procesa en el siguiente.
 */
class ControlStockNegativoSchedulerTest {

    private static ControlStockNegativoProcesador procesadorCon(Long... sucursales) {
        ControlStockNegativoProcesador procesador = mock(ControlStockNegativoProcesador.class);
        when(procesador.sucursales()).thenReturn(Arrays.asList(sucursales));
        return procesador;
    }

    @Test
    void unaSucursalQueFallaNoDetieneALasDemas() {
        ControlStockNegativoProcesador procesador = procesadorCon(1L, 2L, 3L);
        when(procesador.procesarSucursal(2L)).thenThrow(new RuntimeException("timeout"));

        new ControlStockNegativoScheduler(procesador, () -> 0L).ciclo();

        verify(procesador).procesarSucursal(1L);
        verify(procesador).procesarSucursal(2L);
        verify(procesador).procesarSucursal(3L);
    }

    @Test
    void siNoSePuedenListarLasSucursalesElCicloNoRevienta() {
        ControlStockNegativoProcesador procesador = mock(ControlStockNegativoProcesador.class);
        when(procesador.sucursales()).thenThrow(new RuntimeException("sin conexion"));

        new ControlStockNegativoScheduler(procesador, () -> 0L).ciclo();

        verify(procesador, never()).procesarSucursal(anyLong());
    }

    @Test
    void pasadoElPresupuestoDeTiempoCortaYDejaElRestoParaElProximoCiclo() {
        ControlStockNegativoProcesador procesador = procesadorCon(1L, 2L, 3L);
        // Cada lectura del reloj avanza 15 s: arranque en 0, antes de la sucursal 1 van 15 s
        // (dentro del presupuesto de 20), antes de la 2 van 30 s (fuera).
        AtomicLong reloj = new AtomicLong(-15_000);

        new ControlStockNegativoScheduler(procesador, () -> reloj.addAndGet(15_000)).ciclo();

        verify(procesador).procesarSucursal(1L);
        verify(procesador, never()).procesarSucursal(2L);
        verify(procesador, never()).procesarSucursal(3L);
    }

    @Test
    void despuesDeApagarElHiloNoEncolaNiProcesaNada() {
        ControlStockNegativoProcesador procesador = procesadorCon(1L);
        ControlStockNegativoScheduler scheduler = new ControlStockNegativoScheduler(procesador, () -> 0L);

        scheduler.shutdown();
        // El submit se rechaza: no debe propagar la excepcion ni dejar el guard tomado.
        scheduler.evaluarVentas();
        scheduler.evaluarVentas();
        assertFalse(scheduler.estaCorriendo());

        verify(procesador, never()).sucursales();
        verify(procesador, never()).procesarSucursal(anyLong());
    }
}
