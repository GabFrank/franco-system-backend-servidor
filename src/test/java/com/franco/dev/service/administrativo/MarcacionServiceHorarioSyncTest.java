package com.franco.dev.service.administrativo;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.domain.personas.Usuario;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La jornada guarda una copia del horario vigente al momento de la marcacion. Si el horario
 * del funcionario se corrige despues, la jornada tiene que adoptarlo al reprocesarse; antes
 * quedaba pegada al horario viejo para siempre.
 */
class MarcacionServiceHorarioSyncTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 1);
    private static final Long SUCURSAL_ID = 1L;
    private static final Long MARCACION_ID = 101L;

    private MarcacionRepository repository;
    private JornadaService jornadaService;
    private HorarioResolver horarioResolver;
    private JornadaMarcacionResolver jornadaMarcacionResolver;
    private MarcacionService service;

    private Marcacion marcacionEntrada;
    private Jornada jornada;

    @BeforeEach
    void setUp() {
        repository = mock(MarcacionRepository.class);
        jornadaService = mock(JornadaService.class);
        horarioResolver = mock(HorarioResolver.class);
        jornadaMarcacionResolver = mock(JornadaMarcacionResolver.class);

        service = new MarcacionService(
                repository,
                jornadaService,
                horarioResolver,
                jornadaMarcacionResolver,
                new TardanzaCalculator(),
                new HorasTrabajadasCalculator(),
                new AlmuerzoProcessor(),
                new JornadaFactory(),
                mock(JornadaMarcacionRules.class));

        Usuario usuario = new Usuario();
        usuario.setId(494L);

        marcacionEntrada = new Marcacion();
        marcacionEntrada.setId(MARCACION_ID);
        marcacionEntrada.setSucursalId(SUCURSAL_ID);
        marcacionEntrada.setUsuario(usuario);
        marcacionEntrada.setTipo(TipoMarcacion.ENTRADA);
        marcacionEntrada.setFechaEntrada(FECHA.atTime(7, 51));

        Marcacion marcacionSalida = new Marcacion();
        marcacionSalida.setId(102L);
        marcacionSalida.setSucursalId(SUCURSAL_ID);
        marcacionSalida.setTipo(TipoMarcacion.SALIDA);
        marcacionSalida.setFechaSalida(FECHA.atTime(17, 1));

        // Jornada con el horario nocturno viejo ya copiado encima.
        jornada = new Jornada();
        jornada.setId(1105L);
        jornada.setSucursalId(SUCURSAL_ID);
        jornada.setUsuario(usuario);
        jornada.setFecha(FECHA);
        jornada.setEstado(EstadoJornada.NORMAL);
        jornada.setMarcacionEntrada(marcacionEntrada);
        jornada.setMarcacionSalida(marcacionSalida);
        jornada.setTurno(Turno.MADRUGADA);
        jornada.setHoraEntradaHorario(LocalTime.of(17, 0));
        jornada.setHoraSalidaHorario(LocalTime.of(1, 0));

        when(repository.findById(new EmbebedPrimaryKey(MARCACION_ID, SUCURSAL_ID)))
                .thenReturn(Optional.of(marcacionEntrada));
        when(jornadaMarcacionResolver.resolver(any(Marcacion.class), any(LocalDate.class)))
                .thenReturn(jornada);
        when(jornadaService.save(any(Jornada.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Horario horarioDiurno() {
        Horario h = new Horario();
        h.setId(6L);
        h.setTurno(Turno.DIA);
        h.setHoraEntrada(LocalTime.of(8, 0));
        h.setHoraSalida(LocalTime.of(17, 0));
        return h;
    }

    @Test
    void alReprocesar_laJornadaAdoptaElHorarioVigente() {
        when(horarioResolver.resolver(any(Marcacion.class), any(LocalDateTime.class)))
                .thenReturn(horarioDiurno());

        service.reprocesarJornadaDeMarcacion(MARCACION_ID, SUCURSAL_ID);

        assertEquals(Turno.DIA, jornada.getTurno());
        assertEquals(LocalTime.of(8, 0), jornada.getHoraEntradaHorario());
        assertEquals(LocalTime.of(17, 0), jornada.getHoraSalidaHorario());
    }

    @Test
    void alReprocesar_recalculaLasExtrasConElHorarioCorregido() {
        when(horarioResolver.resolver(any(Marcacion.class), any(LocalDateTime.class)))
                .thenReturn(horarioDiurno());

        service.reprocesarJornadaDeMarcacion(MARCACION_ID, SUCURSAL_ID);

        // 07:51 -> 17:01 = 550 min de presencia - 60 de descanso = 490.
        // Horario 08:00-17:00 = 540 - 60 = 480 programados. Extras reales: 10 min,
        // no los 70 que salian comparando contra el turno nocturno 17:00-01:00.
        assertEquals(10L, jornada.getMinutosExtras());
        assertEquals(480L, jornada.getMinutosTrabajados());
    }

    @Test
    void sinHorarioVigente_limpiaElHorarioViejoDeLaJornada() {
        // Caso real: un turno que solo rige lunes y jueves, trabajado un miercoles. Si no
        // rige ningun horario ese dia la jornada tiene que quedar sin horario y medirse
        // contra la jornada estandar; conservar el nocturno viejo mantenia vivo el bug.
        when(horarioResolver.resolver(any(Marcacion.class), any(LocalDateTime.class)))
                .thenReturn(null);

        service.reprocesarJornadaDeMarcacion(MARCACION_ID, SUCURSAL_ID);

        assertNull(jornada.getTurno());
        assertNull(jornada.getHoraEntradaHorario());
        assertNull(jornada.getHoraSalidaHorario());
    }

    @Test
    void sinHorarioVigente_calculaContraLaJornadaEstandarDe8Horas() {
        when(horarioResolver.resolver(any(Marcacion.class), any(LocalDateTime.class)))
                .thenReturn(null);

        service.reprocesarJornadaDeMarcacion(MARCACION_ID, SUCURSAL_ID);

        // 550 min de presencia - 60 de descanso = 490 contra los 480 estandar.
        assertEquals(10L, jornada.getMinutosExtras());
        assertEquals(480L, jornada.getMinutosTrabajados());
    }
}
