package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.EstadoMarcacionUsuario;
import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.AccionMarcacionPendiente;
import com.franco.dev.domain.administrativo.enums.Dia;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.administrativo.helper.*;
import com.franco.dev.service.personas.FuncionarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Turno NOCHE 17:00-01:00. El lunes marca la entrada y se olvida de la salida; el martes es su
 * libre. El miercoles la app le tiene que ofrecer la salida (no la entrada que el central
 * rechazaba) y, al marcarla, la jornada del lunes queda cerrada en 8 horas sin extras.
 */
class SalidaNocturnaOlvidadaTest {

    private static final long USUARIO_ID = 700L;
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
    private static final LocalDate MARTES = LUNES.plusDays(1);
    private static final LocalDate MIERCOLES = LUNES.plusDays(2);

    private JornadaService jornadaService;
    private JornadaMarcacionResolver jornadaMarcacionResolver;
    private JornadaMarcacionRules rules;
    private MarcacionService service;
    private Usuario usuario;
    private final AtomicReference<Jornada> guardada = new AtomicReference<>();
    private long siguienteMarcacionId = 1;

    @BeforeEach
    void setUp() {
        Persona persona = new Persona();
        persona.setId(1L);
        usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setPersona(persona);

        Horario noche = new Horario();
        noche.setId(1L);
        noche.setHoraEntrada(LocalTime.of(17, 0));
        noche.setHoraSalida(LocalTime.of(1, 0));
        noche.setTurno(Turno.NOCHE);
        noche.setDias(new HashSet<>(Collections.singletonList(Dia.TODOS)));
        HorarioRepository horarioRepository = mock(HorarioRepository.class);
        when(horarioRepository.findAsignadosAUsuario(USUARIO_ID))
                .thenReturn(Collections.singletonList(noche));

        jornadaService = mock(JornadaService.class);
        when(jornadaService.save(any(Jornada.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        jornadaMarcacionResolver = mock(JornadaMarcacionResolver.class);
        rules = new JornadaMarcacionRules(jornadaService);

        MarcacionRepository repository = mock(MarcacionRepository.class);
        when(repository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findMaxId(any())).thenAnswer(inv -> siguienteMarcacionId++);

        service = new MarcacionService(repository, jornadaService,
                new HorarioResolver(mock(FuncionarioService.class), horarioRepository),
                jornadaMarcacionResolver, new TardanzaCalculator(), new HorasTrabajadasCalculator(),
                new AlmuerzoProcessor(), new JornadaFactory(), rules);
    }

    private Marcacion marcacion(TipoMarcacion tipo, LocalDateTime cuando) {
        Marcacion m = new Marcacion();
        m.setSucursalId(1L);
        m.setUsuario(usuario);
        m.setTipo(tipo);
        if (tipo == TipoMarcacion.ENTRADA) m.setFechaEntrada(cuando);
        else m.setFechaSalida(cuando);
        return m;
    }

    /** Jornada del lunes con la entrada a las 16:55 y sin salida. */
    private Jornada entradaDelLunes() {
        Jornada j = new Jornada();
        j.setId(11L);
        j.setSucursalId(1L);
        j.setUsuario(usuario);
        j.setFecha(LUNES);
        j.setEstado(EstadoJornada.INCOMPLETO);
        when(jornadaMarcacionResolver.resolver(any(Marcacion.class), any(LocalDate.class))).thenReturn(j);
        service.save(marcacion(TipoMarcacion.ENTRADA, LUNES.atTime(16, 55)));
        return guardada.get();
    }

    /** La jornada sigue abierta: las validaciones del central la encuentran como nocturna pendiente. */
    private void abiertaComoNocturna(Jornada lunes) {
        when(jornadaService.findIncompletaSinSalidaNocturnaParaSalida(eq(USUARIO_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(lunes));
    }

    private Jornada salida(Jornada lunes, LocalDateTime cuando) {
        when(jornadaMarcacionResolver.resolver(any(Marcacion.class), any(LocalDate.class))).thenReturn(lunes);
        service.save(marcacion(TipoMarcacion.SALIDA, cuando));
        return guardada.get();
    }

    @Test
    void elMiercoles_laAppOfreceLaSalidaDefinitivaAunqueLaJornadaSeaDeAntesDeAyer() {
        Jornada lunes = entradaDelLunes();
        abiertaComoNocturna(lunes);
        // La ventana de siempre (ayer y hoy) no trae la jornada del lunes.
        when(jornadaService.findByUsuarioIdAndFechaRange(eq(USUARIO_ID), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        EstadoMarcacionUsuario estado = new EstadoMarcacionService(jornadaService, rules).obtenerEstado(USUARIO_ID);

        assertSame(lunes, estado.getJornadaRelevante());
        assertEquals(AccionMarcacionPendiente.SALIDA_DEFINITIVA, estado.getAccionPendiente());
        assertTrue(estado.isPuedeMarcarSalida());
        assertFalse(estado.isPuedeMarcarSalidaAlmuerzo());
        assertFalse(estado.isPuedeMarcarEntrada());
    }

    @Test
    void laSalidaOlvidadaCierraLaJornadaEnOchoHorasSinExtras() {
        Jornada lunes = entradaDelLunes();
        abiertaComoNocturna(lunes);

        Jornada cerrada = salida(lunes, MIERCOLES.atTime(16, 50));

        assertSame(lunes, cerrada);
        assertEquals(EstadoJornada.NORMAL, cerrada.getEstado());
        assertNotNull(cerrada.getMarcacionSalida());
        assertEquals(480L, cerrada.getMinutosTrabajados());
        assertEquals(0L, cerrada.getMinutosExtras());
        assertTrue(cerrada.getObservacion().startsWith("Salida nocturna olvidada"));
    }

    @Test
    void despuesDeLaSalida_puedeMarcarLaEntradaNormalmente() {
        Jornada lunes = entradaDelLunes();
        abiertaComoNocturna(lunes);
        salida(lunes, MIERCOLES.atTime(16, 50));
        // Ya cerrada, la consulta de nocturnas abiertas no la devuelve mas.
        when(jornadaService.findIncompletaSinSalidaNocturnaParaSalida(eq(USUARIO_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(jornadaService.findJornadaAbiertaConEntradaSinSalida(eq(USUARIO_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> rules.validarEntrada(USUARIO_ID, MIERCOLES));
    }

    @Test
    void unaSalidaNocturnaNormalConExtras_noSeAjusta() {
        Jornada lunes = entradaDelLunes();
        abiertaComoNocturna(lunes);

        // 16:55 -> 03:00 del martes: dos horas de mas, pero el mismo turno.
        Jornada cerrada = salida(lunes, MARTES.atTime(3, 0));

        assertTrue(cerrada.getMinutosExtras() > 0);
        assertNull(cerrada.getObservacion());
    }

    @Test
    void unaJornadaNocturnaEnCurso_sigueOfreciendoSalidaYAlmuerzo() {
        Jornada lunes = entradaDelLunes();

        assertEquals(AccionMarcacionPendiente.SALIDA,
                rules.resolverAccionPendiente(lunes, MARTES, MARTES.atTime(0, 30)));
    }
}
