package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.enums.Dia;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.administrativo.dto.CopiaHorarioResultado;
import com.franco.dev.service.administrativo.helper.HorarioResolver;
import com.franco.dev.service.administrativo.helper.HorarioSemanal;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Horarios compartidos (V284.3): un horario es uno solo y se asigna a varios funcionarios por
 * administrativo.horario_usuario (la cuenta que marca). Editarlo lo cambia para todos los que
 * lo tienen; borrarlo solo se puede cuando ya nadie lo usa.
 */
@Service
@AllArgsConstructor
public class HorarioService extends CrudService<Horario, HorarioRepository, Long> {

    private final HorarioRepository repository;
    private final HorarioResolver horarioResolver;
    private final UsuarioService usuarioService;
    private final FuncionarioRepository funcionarioRepository;

    @Override
    public HorarioRepository getRepository() {
        return repository;
    }

    /** Los horarios asignados al usuario. */
    public List<Horario> findByUsuarioId(Long usuarioId) {
        return repository.findAsignadosAUsuario(usuarioId);
    }

    /** Todos los horarios que se pueden asignar, sin importar a quien. */
    @Transactional(readOnly = true)
    public List<Horario> catalogo() {
        return repository.findCatalogo();
    }

    /**
     * Cuantos funcionarios usan el horario: los que lo tienen asignado o, si nadie, los que
     * todavia lo tienen como principal (funcionario.horario_id, respaldo de la marcacion).
     */
    @Transactional(readOnly = true)
    public int cantidadAsignados(Long horarioId) {
        if (horarioId == null) return 0;
        long asignados = repository.contarAsignados(horarioId);
        if (asignados > 0) return (int) asignados;
        return funcionarioRepository.findByHorarioId(horarioId).size();
    }

    /**
     * Crea o edita un horario del catalogo. Editar cambia el horario para todos los que lo
     * tienen. No se permiten dos iguales: para darselo a otro se asigna el que ya existe.
     */
    @Transactional
    public Horario saveValidado(Horario horario) {
        validarDatos(horario);
        Horario igual = buscarIgual(horario, horario.getId());
        if (igual != null) {
            throw new GraphQLException("Ese horario ya existe (Reg. H. " + igual.getId()
                    + "): asignalo desde la lista en vez de crear otro igual");
        }
        Horario guardado;
        if (horario.getId() != null) {
            guardado = repository.findById(horario.getId())
                    .orElseThrow(() -> new GraphQLException("Horario no encontrado: " + horario.getId()));
            copiarDatos(horario, guardado);
            if (horario.getToleranciaMinutos() != null) guardado.setToleranciaMinutos(horario.getToleranciaMinutos());
            if (horario.getInicioDescanso() != null) guardado.setInicioDescanso(horario.getInicioDescanso());
            if (horario.getFinDescanso() != null) guardado.setFinDescanso(horario.getFinDescanso());
            guardado = repository.save(guardado);
        } else {
            horario.setActivo(true);
            guardado = repository.save(horario);
        }
        // saveHorario con usuarioId (pantallas viejas): ademas se lo asigna.
        if (horario.getUsuario() != null && horario.getUsuario().getId() != null) {
            vincular(horario.getUsuario(), funcionarioDe(horario.getUsuario()), guardado);
        }
        repository.flush();
        return guardado;
    }

    /**
     * Guarda la tabla de horarios de un usuario (pantalla de antes de V284.3): los que traen
     * id se editan (para todos los que los tienen), los nuevos se asignan reusando uno igual
     * si existe, y los que ya no vienen se le quitan, sin borrarlos.
     */
    @Transactional
    public List<Horario> saveDeUsuario(Long usuarioId, List<Horario> horarios) {
        Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));
        Funcionario funcionario = funcionarioDe(usuario);
        List<Horario> lista = horarios == null ? new ArrayList<>() : horarios;
        lista.forEach(this::validarDatos);

        List<Horario> asignados = repository.findAsignadosAUsuario(usuarioId);
        Map<Long, Horario> porId = asignados.stream().collect(Collectors.toMap(Horario::getId, h -> h));
        Set<Long> conservados = new HashSet<>();
        for (Horario h : lista) {
            Horario actual = h.getId() != null ? porId.get(h.getId()) : null;
            Horario destino;
            if (actual != null) {
                copiarDatos(h, actual);
                destino = repository.save(actual);
            } else {
                destino = igualOCrear(h);
            }
            repository.asignar(destino.getId(), usuarioId);
            conservados.add(destino.getId());
        }
        for (Horario viejo : asignados) {
            if (!conservados.contains(viejo.getId())) repository.desasignar(viejo.getId(), usuarioId);
        }
        repository.flush();
        List<Horario> resultado = repository.findAsignadosAUsuario(usuarioId);
        apuntarFuncionario(funcionario, principal(resultado));
        return resultado;
    }

    /**
     * Borra un horario del catalogo. Si alguien lo usa no se borra: hay que quitarselo antes,
     * asi nadie se queda sin horario por accidente.
     */
    @Transactional
    public Boolean eliminar(Long id) {
        Horario horario = repository.findById(id).orElse(null);
        if (horario == null) return false;
        int usan = cantidadAsignados(id);
        if (usan > 0) {
            throw new GraphQLException("No se puede borrar: lo usa" + (usan == 1 ? " 1 funcionario" : "n " + usan
                    + " funcionarios") + ". Quitáselo desde la lista de funcionarios antes de borrarlo.");
        }
        repository.delete(horario);
        return true;
    }

    /**
     * "Asignar a otros": le asigna el mismo horario a cada funcionario, sin copiarlo. El destino
     * es la cuenta que marca, por la persona (funcionario.usuario_id es "creado por").
     */
    @Transactional
    public CopiaHorarioResultado asignar(Long horarioId, List<Long> funcionarioIds) {
        Horario horario = repository.findById(horarioId)
                .orElseThrow(() -> new GraphQLException("Horario no encontrado: " + horarioId));
        CopiaHorarioResultado resultado = new CopiaHorarioResultado();
        for (Long funcionarioId : new LinkedHashSet<>(funcionarioIds)) {
            Funcionario f = funcionarioId == null ? null : funcionarioRepository.findById(funcionarioId).orElse(null);
            if (f == null) continue;
            String nombre = f.getPersona() != null ? f.getPersona().getNombre() : "Funcionario " + f.getId();
            Usuario usuario = usuarioDe(f);
            if (usuario == null) {
                resultado.getErrores().add(nombre + ": no tiene usuario para marcar");
                continue;
            }
            vincular(usuario, f, horario);
            resultado.setCopiados(resultado.getCopiados() + 1);
        }
        repository.flush();
        return resultado;
    }

    /**
     * Los horarios que rigen para el funcionario: los mismos que usa la marcacion (los
     * asignados a su usuario, o el principal si todavia no tiene ninguno asignado).
     */
    @Transactional(readOnly = true)
    public List<Horario> deFuncionario(Long funcionarioId) {
        Funcionario f = funcionarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado: " + funcionarioId));
        Usuario usuario = usuarioDe(f);
        return horarioResolver.horariosVigentes(usuario != null ? usuario.getId() : null, f);
    }

    /**
     * "Nuevo horario" desde la lista de funcionarios: si ya hay uno igual se le asigna ese; si
     * no, se crea en el catalogo y se le asigna.
     */
    @Transactional
    public Funcionario agregarAFuncionario(Long funcionarioId, Horario horario) {
        Funcionario f = funcionarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado: " + funcionarioId));
        Usuario usuario = usuarioDe(f);
        if (usuario == null) {
            throw new GraphQLException("El funcionario no tiene usuario para marcar: no se le puede asignar horario");
        }
        validarDatos(horario);
        vincular(usuario, f, igualOCrear(horario));
        repository.flush();
        return f;
    }

    /** Le quita un horario al funcionario. El horario sigue existiendo para los demas. */
    @Transactional
    public Funcionario quitarDeFuncionario(Long funcionarioId, Long horarioId) {
        Funcionario f = funcionarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado: " + funcionarioId));
        repository.findById(horarioId)
                .orElseThrow(() -> new GraphQLException("Horario no encontrado: " + horarioId));
        Usuario usuario = usuarioDe(f);
        int quitados = usuario != null ? repository.desasignar(horarioId, usuario.getId()) : 0;
        boolean eraPrincipal = f.getHorario() != null && f.getHorario().getId().equals(horarioId);
        if (quitados == 0 && !eraPrincipal) {
            throw new GraphQLException("Ese horario no está asignado a este funcionario");
        }
        if (eraPrincipal) {
            List<Horario> restantes = usuario != null
                    ? repository.findAsignadosAUsuario(usuario.getId()).stream()
                            .filter(h -> !h.getId().equals(horarioId)).collect(Collectors.toList())
                    : Collections.emptyList();
            f.setHorario(principal(restantes));
            funcionarioRepository.save(f);
        }
        repository.flush();
        return funcionarioRepository.findById(funcionarioId).orElse(f);
    }

    /** Horario por dia del funcionario, ej. "L-Mi Día 08:00-17:00 · J Madrugada 17:00-01:00". */
    @Transactional(readOnly = true)
    public String resumen(Funcionario funcionario) {
        Usuario usuario = usuarioDe(funcionario);
        return HorarioSemanal.resumen(
                horarioResolver.horariosVigentes(usuario != null ? usuario.getId() : null, funcionario));
    }

    /**
     * Le asigna el horario al usuario. Si todavia no tenia ninguno asignado y se regia por el
     * principal (funcionario.horario_id), ese tambien se le asigna: al tener uno asignado el
     * principal deja de regir y lo perderia.
     */
    private void vincular(Usuario usuario, Funcionario f, Horario horario) {
        boolean sinAsignados = repository.findAsignadosAUsuario(usuario.getId()).stream()
                .noneMatch(h -> h.getHoraEntrada() != null);
        Horario principal = f != null ? f.getHorario() : null;
        if (sinAsignados && principal != null && principal.getHoraEntrada() != null
                && !principal.getId().equals(horario.getId())) {
            repository.asignar(principal.getId(), usuario.getId());
        }
        repository.asignar(horario.getId(), usuario.getId());
        if (f != null && f.getHorario() == null) {
            f.setHorario(horario);
            funcionarioRepository.save(f);
        }
    }

    private Horario igualOCrear(Horario datos) {
        Horario igual = buscarIgual(datos, null);
        if (igual != null) return igual;
        Horario nuevo = new Horario();
        copiarDatos(datos, nuevo);
        nuevo.setToleranciaMinutos(datos.getToleranciaMinutos());
        nuevo.setInicioDescanso(datos.getInicioDescanso());
        nuevo.setFinDescanso(datos.getFinDescanso());
        nuevo.setActivo(true);
        return repository.save(nuevo);
    }

    /** Otro horario activo con la misma entrada, salida, turno y dias (sin contar {@code salvoId}). */
    private Horario buscarIgual(Horario datos, Long salvoId) {
        return repository.findCatalogo().stream()
                .filter(h -> salvoId == null || !h.getId().equals(salvoId))
                .filter(h -> mismoHorario(h, datos))
                .findFirst()
                .orElse(null);
    }

    private void validarDatos(Horario h) {
        if (h.getHoraEntrada() == null || h.getHoraSalida() == null || h.getTurno() == null) {
            throw new GraphQLException("Cada horario necesita entrada, salida y turno");
        }
        if (h.getHoraEntrada().equals(h.getHoraSalida())) {
            throw new GraphQLException("La entrada y la salida no pueden ser iguales ("
                    + HorarioSemanal.hora(h.getHoraEntrada()) + ")");
        }
        // TODOS junto a dias sueltos es TODOS: los sueltos no agregan nada y confunden.
        if (h.getDias() != null && h.getDias().contains(Dia.TODOS) && h.getDias().size() > 1) {
            h.setDias(new HashSet<>(Collections.singleton(Dia.TODOS)));
        }
    }

    private void copiarDatos(Horario desde, Horario hacia) {
        hacia.setTurno(desde.getTurno());
        hacia.setHoraEntrada(desde.getHoraEntrada());
        hacia.setHoraSalida(desde.getHoraSalida());
        hacia.setDias(desde.getDias() == null ? new HashSet<>() : new HashSet<>(desde.getDias()));
        if (desde.getDescripcion() != null) hacia.setDescripcion(desde.getDescripcion());
    }

    private boolean mismoHorario(Horario a, Horario b) {
        return a.getTurno() == b.getTurno()
                && Objects.equals(a.getHoraEntrada(), b.getHoraEntrada())
                && Objects.equals(a.getHoraSalida(), b.getHoraSalida())
                && Objects.equals(diasEfectivos(a), diasEfectivos(b));
    }

    private Set<Dia> diasEfectivos(Horario h) {
        return HorarioSemanal.esGeneral(h) ? EnumSet.of(Dia.TODOS) : EnumSet.copyOf(h.getDias());
    }

    /**
     * funcionario.horario_id solo es el respaldo de quien no tiene horarios asignados, pero lo
     * leen pantallas viejas: apunta al horario que cubre mas dias.
     */
    private Horario principal(List<Horario> horarios) {
        return horarios.stream()
                .max(Comparator.comparingInt(h -> HorarioSemanal.esGeneral(h) ? 7 : h.getDias().size()))
                .orElse(null);
    }

    private void apuntarFuncionario(Funcionario funcionario, Horario principal) {
        if (funcionario == null) return;
        Long actual = funcionario.getHorario() != null ? funcionario.getHorario().getId() : null;
        Long nuevo = principal != null ? principal.getId() : null;
        if (!Objects.equals(actual, nuevo)) {
            funcionario.setHorario(principal);
            funcionarioRepository.save(funcionario);
        }
    }

    private Usuario usuarioDe(Funcionario f) {
        return f != null && f.getPersona() != null ? usuarioService.findByPersonaId(f.getPersona().getId()) : null;
    }

    private Funcionario funcionarioDe(Usuario usuario) {
        if (usuario == null || usuario.getPersona() == null) return null;
        return funcionarioRepository.findByPersonaId(usuario.getPersona().getId());
    }
}
