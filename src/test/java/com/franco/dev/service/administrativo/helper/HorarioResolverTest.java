package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.Dia;
import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.service.personas.FuncionarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HorarioResolverTest {

    private static final long USUARIO_ID = 544L;
    private static final long PERSONA_ID = 6052L;

    // 2026-08-30 es domingo; 2026-08-31 es lunes.
    private static final LocalDateTime DOMINGO = LocalDateTime.of(2026, 8, 30, 8, 0);
    private static final LocalDateTime LUNES = LocalDateTime.of(2026, 8, 31, 16, 55);

    private FuncionarioService funcionarioService;
    private HorarioRepository horarioRepository;
    private HorarioResolver resolver;
    private Marcacion marcacion;

    @BeforeEach
    void setUp() {
        funcionarioService = mock(FuncionarioService.class);
        horarioRepository = mock(HorarioRepository.class);
        resolver = new HorarioResolver(funcionarioService, horarioRepository);

        Persona persona = new Persona();
        persona.setId(PERSONA_ID);
        Usuario usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setPersona(persona);
        marcacion = new Marcacion();
        marcacion.setUsuario(usuario);

        when(horarioRepository.findByUsuarioIdOrderByIdDesc(anyLong())).thenReturn(Collections.emptyList());
        when(horarioRepository.findByUsuarioIdAndDia(anyLong(), any())).thenReturn(null);
    }

    private Horario horario(Long id, LocalTime entrada, LocalTime salida, Turno turno, Dia... dias) {
        Horario h = new Horario();
        h.setId(id);
        h.setHoraEntrada(entrada);
        h.setHoraSalida(salida);
        h.setTurno(turno);
        h.setDias(dias.length == 0 ? null : new HashSet<>(Arrays.asList(dias)));
        return h;
    }

    private void asignarAlFuncionario(Horario horario) {
        Funcionario funcionario = new Funcionario();
        funcionario.setHorario(horario);
        when(funcionarioService.findByPersonaId(PERSONA_ID)).thenReturn(funcionario);
    }

    @Test
    void horarioDeLunesYJueves_noSeAplicaUnDomingo() {
        // El turno MADRUGADA 17:00-01:00 de ALEXIS PARRA solo rige lunes y jueves, y es el
        // unico horario cargado a su nombre. Un domingo no corresponde ningun horario: la
        // jornada debe quedar sin horario en vez de heredar el nocturno, que le inventaba
        // una hora extra por cada fin de semana trabajado.
        Horario nocturno = horario(4L, LocalTime.of(17, 0), LocalTime.of(1, 0),
                Turno.MADRUGADA, Dia.LUNES, Dia.JUEVES);
        asignarAlFuncionario(nocturno);
        when(horarioRepository.findByUsuarioIdOrderByIdDesc(USUARIO_ID))
                .thenReturn(Collections.singletonList(nocturno));

        assertNull(resolver.resolver(marcacion, DOMINGO));
    }

    @Test
    void horarioDeLunesYJueves_siSeAplicaUnLunes() {
        Horario nocturno = horario(4L, LocalTime.of(17, 0), LocalTime.of(1, 0),
                Turno.MADRUGADA, Dia.LUNES, Dia.JUEVES);
        asignarAlFuncionario(nocturno);

        assertEquals(nocturno, resolver.resolver(marcacion, LUNES));
    }

    @Test
    void horarioTodosLosDias_seAplicaCualquierDia() {
        Horario diurno = horario(6L, LocalTime.of(8, 0), LocalTime.of(17, 0), Turno.DIA, Dia.TODOS);
        asignarAlFuncionario(diurno);

        assertEquals(diurno, resolver.resolver(marcacion, DOMINGO));
    }

    @Test
    void horarioSinDiasConfigurados_seAplicaSiempre() {
        Horario sinDias = horario(1L, LocalTime.of(8, 0), LocalTime.of(17, 0), Turno.DIA);
        asignarAlFuncionario(sinDias);

        assertEquals(sinDias, resolver.resolver(marcacion, DOMINGO));
    }

    @Test
    void siElHorarioDelFuncionarioNoAplica_usaOtroDelUsuarioQueSiAplica() {
        asignarAlFuncionario(horario(4L, LocalTime.of(17, 0), LocalTime.of(1, 0),
                Turno.MADRUGADA, Dia.LUNES, Dia.JUEVES));
        Horario deFinDeSemana = horario(7L, LocalTime.of(8, 0), LocalTime.of(12, 0),
                Turno.DIA, Dia.SABADO, Dia.DOMINGO);
        when(horarioRepository.findByUsuarioIdOrderByIdDesc(USUARIO_ID))
                .thenReturn(Collections.singletonList(deFinDeSemana));

        assertEquals(deFinDeSemana, resolver.resolver(marcacion, DOMINGO));
    }

    @Test
    void sinFuncionario_buscaHorarioDelUsuarioParaEseDia() {
        when(funcionarioService.findByPersonaId(PERSONA_ID)).thenReturn(null);
        Horario diurno = horario(6L, LocalTime.of(8, 0), LocalTime.of(17, 0), Turno.DIA, Dia.TODOS);
        when(horarioRepository.findByUsuarioIdAndDia(USUARIO_ID, Dia.DOMINGO)).thenReturn(diurno);

        assertEquals(diurno, resolver.resolver(marcacion, DOMINGO));
    }

    @Test
    void sinHorarioEnNingunLado_devuelveNull() {
        when(funcionarioService.findByPersonaId(PERSONA_ID)).thenReturn(null);

        assertNull(resolver.resolver(marcacion, DOMINGO));
    }

    @Test
    void horariosDelUsuarioSinHoraEntrada_seIgnoran() {
        asignarAlFuncionario(horario(4L, LocalTime.of(17, 0), LocalTime.of(1, 0),
                Turno.MADRUGADA, Dia.LUNES, Dia.JUEVES));
        Horario incompleto = horario(8L, null, null, Turno.DIA, Dia.TODOS);
        when(horarioRepository.findByUsuarioIdOrderByIdDesc(USUARIO_ID))
                .thenReturn(new java.util.ArrayList<>(Set.of(incompleto)));

        assertNull(resolver.resolver(marcacion, DOMINGO));
    }
}
