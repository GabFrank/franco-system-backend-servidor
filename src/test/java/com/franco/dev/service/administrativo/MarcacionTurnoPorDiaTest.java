package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #523: un funcionario con turno DIA (08:00-17:00) de lunes a miercoles y viernes, y
 * MADRUGADA (17:00-01:00) los jueves. El turno se fija por el dia de la jornada, y la regla
 * de siempre sigue igual: la jornada de DIA vence a medianoche y la nocturna admite la
 * salida al dia siguiente aunque ese dia tenga otro turno.
 */
class MarcacionTurnoPorDiaTest {

    private static final long USUARIO_ID = 544L;
    private static final LocalDate LUNES = LocalDate.of(2026, 8, 31);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 2);
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 3);
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 4);

    private JornadaMarcacionResolver jornadaMarcacionResolver;
    private JornadaMarcacionRules rules;
    private MarcacionService service;
    private Usuario usuario;
    private final AtomicReference<Jornada> guardada = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Persona persona = new Persona();
        persona.setId(556L);
        usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setPersona(persona);

        HorarioRepository horarioRepository = mock(HorarioRepository.class);
        when(horarioRepository.findByUsuarioIdOrderByIdDesc(USUARIO_ID)).thenReturn(Arrays.asList(
                horario(21L, 17, 1, Turno.MADRUGADA, Dia.JUEVES),
                horario(20L, 8, 17, Turno.DIA, Dia.LUNES, Dia.MARTES, Dia.MIERCOLES, Dia.VIERNES)));
        HorarioResolver horarioResolver = new HorarioResolver(mock(FuncionarioService.class), horarioRepository);

        JornadaService jornadaService = mock(JornadaService.class);
        when(jornadaService.save(any(Jornada.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        jornadaMarcacionResolver = mock(JornadaMarcacionResolver.class);
        rules = new JornadaMarcacionRules(jornadaService);

        MarcacionRepository repository = mock(MarcacionRepository.class);
        when(repository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findMaxId(any())).thenReturn(0L);

        service = new MarcacionService(repository, jornadaService, horarioResolver, jornadaMarcacionResolver,
                new TardanzaCalculator(), new HorasTrabajadasCalculator(), new AlmuerzoProcessor(),
                new JornadaFactory(), mock(JornadaMarcacionRules.class));
    }

    private Horario horario(Long id, int entrada, int salida, Turno turno, Dia... dias) {
        Horario h = new Horario();
        h.setId(id);
        h.setHoraEntrada(LocalTime.of(entrada, 0));
        h.setHoraSalida(LocalTime.of(salida, 0));
        h.setTurno(turno);
        h.setDias(new HashSet<>(Arrays.asList(dias)));
        return h;
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

    private Jornada jornadaNueva(LocalDate fecha) {
        Jornada j = new Jornada();
        j.setId(1L);
        j.setSucursalId(1L);
        j.setUsuario(usuario);
        j.setFecha(fecha);
        j.setEstado(EstadoJornada.INCOMPLETO);
        return j;
    }

    private Jornada entrada(LocalDate fecha, LocalTime hora) {
        Jornada jornada = jornadaNueva(fecha);
        when(jornadaMarcacionResolver.resolver(any(Marcacion.class), any(LocalDate.class))).thenReturn(jornada);
        service.save(marcacion(TipoMarcacion.ENTRADA, fecha.atTime(hora)));
        return guardada.get();
    }

    @Test
    void elJueves_laJornadaEsDeMadrugada() {
        Jornada jornada = entrada(JUEVES, LocalTime.of(16, 55));

        assertEquals(Turno.MADRUGADA, jornada.getTurno());
        assertEquals(LocalTime.of(17, 0), jornada.getHoraEntradaHorario());
        assertEquals(LocalTime.of(1, 0), jornada.getHoraSalidaHorario());
    }

    @Test
    void elLunes_laJornadaEsDeDia() {
        assertEquals(Turno.DIA, entrada(LUNES, LocalTime.of(7, 55)).getTurno());
    }

    @Test
    void laSalidaDelViernesALaUna_conservaElTurnoDelJueves() {
        Jornada jueves = entrada(JUEVES, LocalTime.of(16, 55));
        // El viernes rige DIA, pero la salida es de la jornada del jueves.
        when(jornadaMarcacionResolver.resolver(any(Marcacion.class), any(LocalDate.class))).thenReturn(jueves);

        service.save(marcacion(TipoMarcacion.SALIDA, VIERNES.atTime(1, 0)));

        Jornada cerrada = guardada.get();
        assertSame(jueves, cerrada);
        assertEquals(JUEVES, cerrada.getFecha());
        assertEquals(Turno.MADRUGADA, cerrada.getTurno());
        assertNotNull(cerrada.getMarcacionSalida());
    }

    @Test
    void laJornadaDeMadrugadaDelJueves_sigueActivaElViernes() {
        Jornada jueves = entrada(JUEVES, LocalTime.of(16, 55));

        assertTrue(rules.cruzaMedianoche(jueves));
        assertTrue(rules.esJornadaActiva(jueves, VIERNES), "puede marcar la salida al dia siguiente");
    }

    @Test
    void laJornadaDeDiaSinSalida_venceAMedianoche() {
        // Se olvido de marcar la salida el miercoles (DIA): el jueves vuelve a marcar entrada.
        Jornada miercoles = entrada(MIERCOLES, LocalTime.of(7, 55));

        assertEquals(Turno.DIA, miercoles.getTurno());
        assertFalse(rules.esJornadaActiva(miercoles, JUEVES));
        assertEquals(com.franco.dev.domain.administrativo.enums.AccionMarcacionPendiente.ENTRADA,
                rules.resolverAccionPendiente(miercoles, JUEVES));
    }

    @Test
    void conDosTurnosElMismoDia_laSalidaConservaElTurnoDeLaEntrada() {
        // Miercoles con dos horarios: Dia 07:00-12:00 y Madrugada 17:00-01:00. Entra 16:50, es
        // la madrugada. La salida del jueves 06:30 queda mas cerca de las 07:00 del turno Dia,
        // pero el horario se elige por la hora de la entrada: sigue siendo madrugada.
        HorarioRepository repo = mock(HorarioRepository.class);
        when(repo.findByUsuarioIdOrderByIdDesc(USUARIO_ID)).thenReturn(Arrays.asList(
                horario(31L, 7, 12, Turno.DIA, Dia.MIERCOLES),
                horario(32L, 17, 1, Turno.MADRUGADA, Dia.MIERCOLES)));
        JornadaService jornadaService = mock(JornadaService.class);
        when(jornadaService.save(any(Jornada.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        MarcacionRepository repository = mock(MarcacionRepository.class);
        when(repository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new MarcacionService(repository, jornadaService,
                new HorarioResolver(mock(FuncionarioService.class), repo), jornadaMarcacionResolver,
                new TardanzaCalculator(), new HorasTrabajadasCalculator(), new AlmuerzoProcessor(),
                new JornadaFactory(), mock(JornadaMarcacionRules.class));

        Jornada miercoles = entrada(MIERCOLES, LocalTime.of(16, 50));
        assertEquals(Turno.MADRUGADA, miercoles.getTurno());

        when(jornadaMarcacionResolver.resolver(any(Marcacion.class), any(LocalDate.class))).thenReturn(miercoles);
        service.save(marcacion(TipoMarcacion.SALIDA, JUEVES.atTime(6, 30)));

        assertEquals(Turno.MADRUGADA, guardada.get().getTurno());
        assertEquals(LocalTime.of(17, 0), guardada.get().getHoraEntradaHorario());
    }

    @Test
    void conDosTurnosElMismoDia_laEntradaTempranaEsDelTurnoDia() {
        HorarioRepository repo = mock(HorarioRepository.class);
        when(repo.findByUsuarioIdOrderByIdDesc(USUARIO_ID)).thenReturn(Arrays.asList(
                horario(31L, 7, 12, Turno.DIA, Dia.MIERCOLES),
                horario(32L, 17, 1, Turno.MADRUGADA, Dia.MIERCOLES)));
        JornadaService jornadaService = mock(JornadaService.class);
        when(jornadaService.save(any(Jornada.class))).thenAnswer(inv -> {
            guardada.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        MarcacionRepository repository = mock(MarcacionRepository.class);
        when(repository.save(any(Marcacion.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new MarcacionService(repository, jornadaService,
                new HorarioResolver(mock(FuncionarioService.class), repo), jornadaMarcacionResolver,
                new TardanzaCalculator(), new HorasTrabajadasCalculator(), new AlmuerzoProcessor(),
                new JornadaFactory(), mock(JornadaMarcacionRules.class));

        Jornada miercoles = entrada(MIERCOLES, LocalTime.of(6, 55));
        assertEquals(Turno.DIA, miercoles.getTurno());
        assertFalse(rules.esJornadaActiva(miercoles, JUEVES), "la de dia vence a medianoche");
    }
}
