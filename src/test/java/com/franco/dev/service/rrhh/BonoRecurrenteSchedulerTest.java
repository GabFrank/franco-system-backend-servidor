package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * El bucle vive en el scheduler y no dentro del service para que cada plantilla
 * tenga su propia transaccion (llamar generarUno desde otro metodo del mismo
 * bean se saltea el proxy de Spring y el @Transactional no aplica).
 *
 * Este test es el que fija esa decision: si alguien mueve el bucle adentro del
 * service, una plantilla que explota se lleva puestas a las demas y esto falla.
 */
class BonoRecurrenteSchedulerTest {

    private static final YearMonth MARZO = YearMonth.of(2026, 3);

    @Test
    void unaPlantillaQueFallaNoImpideQueSeGenerenLasDemas() {
        BonoRecurrenteService service = mock(BonoRecurrenteService.class);
        when(service.plantillasActivas()).thenReturn(List.of(1L, 2L, 3L));
        when(service.generarUno(1L, MARZO)).thenReturn(Optional.of(new Bono()));
        when(service.generarUno(2L, MARZO)).thenThrow(new RuntimeException("funcionario roto"));
        when(service.generarUno(3L, MARZO)).thenReturn(Optional.of(new Bono()));

        BonoRecurrenteScheduler scheduler = new BonoRecurrenteScheduler(service);

        assertEquals(2, scheduler.generarPeriodo(MARZO));
        verify(service).generarUno(3L, MARZO);
    }

    @Test
    void noCuentaLasPlantillasQueNoGeneraronNada() {
        BonoRecurrenteService service = mock(BonoRecurrenteService.class);
        when(service.plantillasActivas()).thenReturn(List.of(1L, 2L));
        when(service.generarUno(1L, MARZO)).thenReturn(Optional.of(new Bono()));
        when(service.generarUno(2L, MARZO)).thenReturn(Optional.empty());

        BonoRecurrenteScheduler scheduler = new BonoRecurrenteScheduler(service);

        assertEquals(1, scheduler.generarPeriodo(MARZO));
    }

    @Test
    void sinPlantillasActivasNoHaceNada() {
        BonoRecurrenteService service = mock(BonoRecurrenteService.class);
        when(service.plantillasActivas()).thenReturn(List.of());

        BonoRecurrenteScheduler scheduler = new BonoRecurrenteScheduler(service);

        assertEquals(0, scheduler.generarPeriodo(MARZO));
        verify(service, never()).generarUno(anyLong(), any());
    }
}
