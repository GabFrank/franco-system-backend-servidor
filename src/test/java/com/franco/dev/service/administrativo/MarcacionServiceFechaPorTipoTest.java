package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.administrativo.helper.AlmuerzoProcessor;
import com.franco.dev.service.administrativo.helper.HorarioResolver;
import com.franco.dev.service.administrativo.helper.HorasTrabajadasCalculator;
import com.franco.dev.service.administrativo.helper.JornadaFactory;
import com.franco.dev.service.administrativo.helper.JornadaMarcacionResolver;
import com.franco.dev.service.administrativo.helper.JornadaMarcacionRules;
import com.franco.dev.service.administrativo.helper.TardanzaCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Una marcacion nueva que llega sin ninguna fecha —la PWA no manda fecha, la pone el servidor—
 * tiene que guardar la hora en el campo de su tipo. Antes una SALIDA quedaba en fechaEntrada con
 * fechaSalida nula, y el desktop y frc-mobile, que leen fechaSalida, la mostraban vacia o
 * "En Curso".
 *
 * Las marcaciones van sin usuario: asi save() no procesa la jornada y lo que se prueba es solo
 * que fecha se completa.
 */
class MarcacionServiceFechaPorTipoTest {

    private MarcacionService service;

    @BeforeEach
    void setUp() {
        MarcacionRepository repository = mock(MarcacionRepository.class);
        when(repository.findMaxId(any())).thenReturn(10L);
        when(repository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new MarcacionService(
                repository,
                mock(JornadaService.class),
                mock(HorarioResolver.class),
                mock(JornadaMarcacionResolver.class),
                new TardanzaCalculator(),
                new HorasTrabajadasCalculator(),
                new AlmuerzoProcessor(),
                new JornadaFactory(),
                mock(JornadaMarcacionRules.class));
    }

    private static Marcacion nueva(TipoMarcacion tipo) {
        Marcacion m = new Marcacion();
        m.setSucursalId(2L);
        m.setTipo(tipo);
        return m;
    }

    private static void esAhora(LocalDateTime fecha) {
        assertNotNull(fecha);
        assertTrue(Duration.between(fecha, LocalDateTime.now()).abs().getSeconds() < 60,
                "la fecha completada tiene que ser la del servidor, ahora: " + fecha);
    }

    @Test
    void salidaSinFecha_guardaLaHoraEnFechaSalida() {
        Marcacion guardada = service.save(nueva(TipoMarcacion.SALIDA));

        esAhora(guardada.getFechaSalida());
        assertNull(guardada.getFechaEntrada());
        assertEquals(TipoMarcacion.SALIDA, guardada.getTipo());
    }

    @Test
    void entradaSinFecha_guardaLaHoraEnFechaEntrada() {
        Marcacion guardada = service.save(nueva(TipoMarcacion.ENTRADA));

        esAhora(guardada.getFechaEntrada());
        assertNull(guardada.getFechaSalida());
    }

    @Test
    void sinTipoNiFecha_siguenSiendoUnaEntrada() {
        Marcacion guardada = service.save(nueva(null));

        esAhora(guardada.getFechaEntrada());
        assertNull(guardada.getFechaSalida());
        assertEquals(TipoMarcacion.ENTRADA, guardada.getTipo());
    }

    @Test
    void salidaConFechaDelCliente_noSePisa() {
        // El desktop manda la hora sincronizada con el servidor: esa es la que vale.
        LocalDateTime delCliente = LocalDateTime.of(2026, 9, 25, 18, 0);
        Marcacion m = nueva(TipoMarcacion.SALIDA);
        m.setFechaSalida(delCliente);

        Marcacion guardada = service.save(m);

        assertEquals(delCliente, guardada.getFechaSalida());
        assertNull(guardada.getFechaEntrada());
    }

    @Test
    void entradaConFechaDelCliente_noSePisa() {
        LocalDateTime delCliente = LocalDateTime.of(2026, 9, 25, 8, 0);
        Marcacion m = nueva(TipoMarcacion.ENTRADA);
        m.setFechaEntrada(delCliente);

        Marcacion guardada = service.save(m);

        assertEquals(delCliente, guardada.getFechaEntrada());
        assertNull(guardada.getFechaSalida());
    }
}
