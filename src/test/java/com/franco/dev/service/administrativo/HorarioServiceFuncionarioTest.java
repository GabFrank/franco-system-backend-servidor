package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.service.administrativo.helper.HorarioResolver;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Agregarle y quitarle horarios a un funcionario desde la lista (#523). */
class HorarioServiceFuncionarioTest {

    private HorarioRepository repository;
    private FuncionarioRepository funcionarioRepository;
    private HorarioService service;
    private Funcionario funcionario;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        repository = mock(HorarioRepository.class);
        funcionarioRepository = mock(FuncionarioRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        service = new HorarioService(repository, mock(HorarioResolver.class), usuarioService, funcionarioRepository);

        Persona persona = new Persona();
        persona.setId(494L);
        usuario = new Usuario();
        usuario.setId(494L);
        funcionario = new Funcionario();
        funcionario.setId(507L);
        funcionario.setPersona(persona);
        when(funcionarioRepository.findById(507L)).thenReturn(Optional.of(funcionario));
        when(usuarioService.findByPersonaId(494L)).thenReturn(usuario);
        when(funcionarioRepository.findByHorarioId(anyLong())).thenReturn(Collections.emptyList());
    }

    private Horario horario(Long id, Usuario dueno) {
        Horario h = new Horario();
        h.setId(id);
        h.setUsuario(dueno);
        when(repository.findById(id)).thenReturn(Optional.of(h));
        return h;
    }

    @Test
    void unHorarioPropio_seBorra() {
        Horario h = horario(10L, usuario);

        service.quitarDeFuncionario(507L, 10L);

        verify(repository).delete(h);
    }

    @Test
    void elPrincipalCompartido_soloSeDesvincula() {
        Usuario otro = new Usuario();
        otro.setId(123L);
        Horario compartido = horario(6L, otro);
        funcionario.setHorario(compartido);
        Funcionario vecino = new Funcionario();
        vecino.setId(112L);
        when(funcionarioRepository.findByHorarioId(6L)).thenReturn(Arrays.asList(funcionario, vecino));

        service.quitarDeFuncionario(507L, 6L);

        assertNull(funcionario.getHorario());
        verify(repository, never()).delete(any(Horario.class));
        assertEquals(otro, compartido.getUsuario(), "los demas funcionarios lo conservan");
    }

    @Test
    void unHorarioAjeno_daError() {
        Usuario otro = new Usuario();
        otro.setId(123L);
        horario(6L, otro);

        assertThrows(GraphQLException.class, () -> service.quitarDeFuncionario(507L, 6L));
    }

    @Test
    void agregar_creaElHorarioComoPropioDelUsuario() {
        when(repository.findByUsuarioIdOrderByIdDesc(494L)).thenReturn(Collections.emptyList());
        when(repository.save(any(Horario.class))).thenAnswer(inv -> inv.getArgument(0));
        Horario input = new Horario();
        input.setTurno(com.franco.dev.domain.administrativo.enums.Turno.NOCHE);
        input.setHoraEntrada(java.time.LocalTime.of(18, 0));
        input.setHoraSalida(java.time.LocalTime.of(23, 0));

        service.agregarAFuncionario(507L, input);

        assertNotNull(funcionario.getHorario());
        assertEquals(usuario, funcionario.getHorario().getUsuario());
        assertEquals(java.time.LocalTime.of(18, 0), funcionario.getHorario().getHoraEntrada());
    }

    @Test
    void agregar_aQuienUsaElCompartido_leCopiaElCompartidoParaQueNoLoPierda() {
        Usuario otro = new Usuario();
        otro.setId(123L);
        Horario compartido = horario(6L, otro);
        compartido.setTurno(com.franco.dev.domain.administrativo.enums.Turno.DIA);
        compartido.setHoraEntrada(java.time.LocalTime.of(8, 0));
        compartido.setHoraSalida(java.time.LocalTime.of(17, 0));
        funcionario.setHorario(compartido);
        when(repository.findByUsuarioIdOrderByIdDesc(494L)).thenReturn(Collections.emptyList());
        java.util.List<Horario> guardados = new java.util.ArrayList<>();
        when(repository.save(any(Horario.class))).thenAnswer(inv -> {
            guardados.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        Horario input = new Horario();
        input.setTurno(com.franco.dev.domain.administrativo.enums.Turno.NOCHE);
        input.setHoraEntrada(java.time.LocalTime.of(18, 0));
        input.setHoraSalida(java.time.LocalTime.of(23, 0));

        service.agregarAFuncionario(507L, input);

        assertEquals(2, guardados.size(), "la copia del compartido y el nuevo");
        assertEquals(java.time.LocalTime.of(8, 0), guardados.get(0).getHoraEntrada());
        assertEquals(usuario, guardados.get(0).getUsuario());
        assertEquals(otro, compartido.getUsuario(), "el compartido no se toca");
    }
}
