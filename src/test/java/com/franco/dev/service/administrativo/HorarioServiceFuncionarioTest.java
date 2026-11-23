package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.enums.Dia;
import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.service.administrativo.dto.CopiaHorarioResultado;
import com.franco.dev.service.administrativo.helper.HorarioResolver;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Horarios compartidos (V284.3): un horario se asigna a varios funcionarios sin copiarlo,
 * editarlo lo cambia para todos y no se puede borrar mientras alguien lo use.
 */
class HorarioServiceFuncionarioTest {

    private HorarioRepository repository;
    private FuncionarioRepository funcionarioRepository;
    private UsuarioService usuarioService;
    private HorarioService service;
    private Funcionario funcionario;
    private Usuario usuario;
    /** Asignaciones (horario_usuario) en memoria: horarioId -> usuarioIds. */
    private final Map<Long, Set<Long>> asignaciones = new HashMap<>();
    private final Map<Long, Horario> horarios = new HashMap<>();
    private long siguienteId = 100;

    @BeforeEach
    void setUp() {
        repository = mock(HorarioRepository.class);
        funcionarioRepository = mock(FuncionarioRepository.class);
        usuarioService = mock(UsuarioService.class);
        service = new HorarioService(repository, mock(HorarioResolver.class), usuarioService, funcionarioRepository);

        funcionario = funcionario(507L, 494L);
        usuario = usuarioService.findByPersonaId(494L);
        when(funcionarioRepository.findByHorarioId(anyLong())).thenAnswer(inv -> Collections.emptyList());

        when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(horarios.get((Long) inv.getArgument(0))));
        when(repository.save(any(Horario.class))).thenAnswer(inv -> {
            Horario h = inv.getArgument(0);
            if (h.getId() == null) h.setId(siguienteId++);
            horarios.put(h.getId(), h);
            return h;
        });
        when(repository.findCatalogo()).thenAnswer(inv -> new ArrayList<>(horarios.values()));
        when(repository.asignar(anyLong(), anyLong())).thenAnswer(inv -> {
            boolean nuevo = asignaciones.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return nuevo ? 1 : 0;
        });
        when(repository.desasignar(anyLong(), anyLong())).thenAnswer(inv -> {
            Set<Long> u = asignaciones.get((Long) inv.getArgument(0));
            return u != null && u.remove((Long) inv.getArgument(1)) ? 1 : 0;
        });
        when(repository.contarAsignados(anyLong())).thenAnswer(inv ->
                (long) asignaciones.getOrDefault((Long) inv.getArgument(0), Collections.emptySet()).size());
        when(repository.findAsignadosAUsuario(anyLong())).thenAnswer(inv -> {
            Long u = inv.getArgument(0);
            List<Horario> res = new ArrayList<>();
            asignaciones.forEach((h, us) -> { if (us.contains(u)) res.add(horarios.get(h)); });
            return res;
        });
    }

    private Funcionario funcionario(Long id, Long personaId) {
        Persona persona = new Persona();
        persona.setId(personaId);
        persona.setNombre("Funcionario " + id);
        Usuario u = new Usuario();
        u.setId(personaId);
        u.setPersona(persona);
        Funcionario f = new Funcionario();
        f.setId(id);
        f.setPersona(persona);
        when(funcionarioRepository.findById(id)).thenReturn(Optional.of(f));
        when(usuarioService.findByPersonaId(personaId)).thenReturn(u);
        return f;
    }

    private Horario horario(int entrada, int salida, Turno turno) {
        Horario h = new Horario();
        h.setHoraEntrada(LocalTime.of(entrada, 0));
        h.setHoraSalida(LocalTime.of(salida, 0));
        h.setTurno(turno);
        h.setDias(new HashSet<>(Collections.singleton(Dia.TODOS)));
        return h;
    }

    private Horario enCatalogo(int entrada, int salida, Turno turno) {
        return repository.save(horario(entrada, salida, turno));
    }

    @Test
    void asignarAVarios_usaElMismoHorarioSinCopiarlo() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);
        funcionario(508L, 495L);
        funcionario(509L, 496L);

        CopiaHorarioResultado r = service.asignar(noche.getId(), Arrays.asList(507L, 508L, 509L));

        assertEquals(3, r.getCopiados());
        assertTrue(r.getErrores().isEmpty());
        assertEquals(new HashSet<>(Arrays.asList(494L, 495L, 496L)), asignaciones.get(noche.getId()));
        assertEquals(1, horarios.size(), "no se crea ninguna copia");
        assertEquals(noche, funcionario.getHorario(), "sin principal, pasa a ser el principal");
    }

    @Test
    void asignar_aQuienSeRegiaPorElPrincipal_tambienLeAsignaElPrincipal() {
        Horario dia = enCatalogo(8, 17, Turno.DIA);
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);
        funcionario.setHorario(dia);

        service.asignar(noche.getId(), Collections.singletonList(507L));

        assertTrue(asignaciones.get(dia.getId()).contains(494L), "no pierde el que ya tenia");
        assertTrue(asignaciones.get(noche.getId()).contains(494L));
        assertEquals(dia, funcionario.getHorario());
    }

    @Test
    void asignar_aQuienNoTieneUsuario_loInforma() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);
        Funcionario sinUsuario = funcionario(510L, 497L);
        when(usuarioService.findByPersonaId(497L)).thenReturn(null);

        CopiaHorarioResultado r = service.asignar(noche.getId(), Collections.singletonList(sinUsuario.getId()));

        assertEquals(0, r.getCopiados());
        assertEquals(1, r.getErrores().size());
    }

    @Test
    void editar_cambiaElHorarioParaTodos() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);
        service.asignar(noche.getId(), Collections.singletonList(507L));
        Horario cambio = horario(18, 2, Turno.NOCHE);
        cambio.setId(noche.getId());

        service.saveValidado(cambio);

        assertEquals(1, horarios.size());
        assertEquals(LocalTime.of(18, 0), horarios.get(noche.getId()).getHoraEntrada());
        assertTrue(asignaciones.get(noche.getId()).contains(494L));
    }

    @Test
    void crearUnoIgualAOtro_daError() {
        enCatalogo(8, 17, Turno.DIA);

        assertThrows(GraphQLException.class, () -> service.saveValidado(horario(8, 17, Turno.DIA)));
    }

    @Test
    void borrarUnoQueAlguienUsa_noSeBorra() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);
        service.asignar(noche.getId(), Collections.singletonList(507L));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.eliminar(noche.getId()));
        assertTrue(e.getMessage().contains("1 funcionario"), e.getMessage());
        verify(repository, never()).delete(any(Horario.class));
    }

    @Test
    void borrarUnoQueEsPrincipalDeAlguien_noSeBorra() {
        Horario dia = enCatalogo(8, 17, Turno.DIA);
        when(funcionarioRepository.findByHorarioId(dia.getId())).thenReturn(Collections.singletonList(funcionario));

        assertThrows(GraphQLException.class, () -> service.eliminar(dia.getId()));
        verify(repository, never()).delete(any(Horario.class));
    }

    @Test
    void borrarUnoSinUso_seBorra() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);

        assertTrue(service.eliminar(noche.getId()));
        verify(repository).delete(noche);
    }

    @Test
    void quitar_soloLoDesasignaYLosDemasLoConservan() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);
        funcionario(508L, 495L);
        service.asignar(noche.getId(), Arrays.asList(507L, 508L));

        service.quitarDeFuncionario(507L, noche.getId());

        assertEquals(Collections.singleton(495L), asignaciones.get(noche.getId()));
        assertNull(funcionario.getHorario(), "era su principal y no le queda otro");
        verify(repository, never()).delete(any(Horario.class));
    }

    @Test
    void quitarUnoQueNoTiene_daError() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);

        assertThrows(GraphQLException.class, () -> service.quitarDeFuncionario(507L, noche.getId()));
    }

    @Test
    void nuevoHorarioDesdeElFuncionario_reusaUnoIgual() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);

        service.agregarAFuncionario(507L, horario(17, 1, Turno.NOCHE));

        assertEquals(1, horarios.size(), "no crea un repetido");
        assertTrue(asignaciones.get(noche.getId()).contains(494L));
    }

    @Test
    void nuevoHorarioDesdeElFuncionario_siNoExisteLoCreaYLoAsigna() {
        service.agregarAFuncionario(507L, horario(18, 23, Turno.NOCHE));

        assertEquals(1, horarios.size());
        Horario creado = horarios.values().iterator().next();
        assertTrue(asignaciones.get(creado.getId()).contains(494L));
        assertNull(creado.getUsuario(), "el horario no tiene dueño");
        assertEquals(creado, funcionario.getHorario());
    }

    @Test
    void cantidadAsignados_cuentaLosQueLoTienen() {
        Horario noche = enCatalogo(17, 1, Turno.NOCHE);
        funcionario(508L, 495L);
        service.asignar(noche.getId(), Arrays.asList(507L, 508L));

        assertEquals(2, service.cantidadAsignados(noche.getId()));
    }
}
