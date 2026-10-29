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

    public List<Horario> findByUsuarioId(Long usuarioId) {
        return repository.findByUsuarioIdOrderByIdDesc(usuarioId);
    }

    /**
     * Guarda un horario suelto (mutation saveHorario). No se compara con los otros del usuario:
     * pueden pisarse (#523), al marcar gana el de entrada mas cercana.
     */
    @Transactional
    public Horario saveValidado(Horario horario) {
        validarDatos(horario);
        return save(horario);
    }

    /**
     * Guarda la tabla de horarios del usuario de una vez (#523): los que traen id se
     * actualizan, los nuevos se crean y los del usuario que ya no vienen se quitan. Se valida
     * Los horarios pueden pisarse entre si: es a proposito, ver HorarioSemanal.
     */
    @Transactional
    public List<Horario> saveDeUsuario(Long usuarioId, List<Horario> horarios) {
        Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));
        Funcionario funcionario = funcionarioDe(usuario);
        List<Horario> lista = horarios == null ? new ArrayList<>() : horarios;
        lista.forEach(this::validarDatos);

        List<Horario> existentes = repository.findByUsuarioIdOrderByIdDesc(usuarioId);
        Map<Long, Horario> porId = existentes.stream().collect(Collectors.toMap(Horario::getId, h -> h));
        Set<Long> conservados = new HashSet<>();
        List<Horario> guardados = new ArrayList<>();
        for (Horario h : lista) {
            Horario actual = h.getId() != null ? porId.get(h.getId()) : null;
            if (actual != null && !compartido(actual, funcionario)) {
                copiarDatos(h, actual);
                conservados.add(actual.getId());
                guardados.add(repository.save(actual));
            } else {
                // Nuevo, o uno que comparten otros funcionarios (cargado antes de #523): se
                // crea uno propio para no cambiarle el horario a nadie mas.
                Horario nuevo = new Horario();
                if (actual != null) {
                    nuevo.setToleranciaMinutos(actual.getToleranciaMinutos());
                    nuevo.setInicioDescanso(actual.getInicioDescanso());
                    nuevo.setFinDescanso(actual.getFinDescanso());
                }
                copiarDatos(h, nuevo);
                nuevo.setUsuario(usuario);
                guardados.add(repository.save(nuevo));
            }
        }

        Horario principal = principal(guardados);
        for (Horario viejo : existentes) {
            if (!conservados.contains(viejo.getId())) quitar(viejo, funcionario, principal);
        }
        apuntarFuncionario(funcionario, principal);
        repository.flush();
        return repository.findByUsuarioIdOrderByIdDesc(usuarioId);
    }

    /** Borra un horario de la tabla. Quien lo tenia como principal pasa a otro suyo, o a ninguno. */
    @Transactional
    public Boolean eliminar(Long id) {
        Horario horario = repository.findById(id).orElse(null);
        if (horario == null) return false;
        List<Horario> otros = horario.getUsuario() == null ? Collections.emptyList()
                : repository.findByUsuarioIdOrderByIdDesc(horario.getUsuario().getId()).stream()
                        .filter(h -> !h.getId().equals(id)).collect(Collectors.toList());
        Horario reemplazo = principal(otros);
        for (Funcionario f : funcionarioRepository.findByHorarioId(id)) {
            f.setHorario(reemplazo);
            funcionarioRepository.save(f);
        }
        repository.delete(horario);
        return true;
    }

    /**
     * "Asignar a otros": copia el horario a cada funcionario como horario propio. El destino
     * es la cuenta que marca, por la persona (funcionario.usuario_id es "creado por"). Si ya
     * tiene uno igual no se duplica.
     */
    @Transactional
    public CopiaHorarioResultado copiar(Long horarioId, List<Long> funcionarioIds) {
        Horario origen = repository.findById(horarioId)
                .orElseThrow(() -> new GraphQLException("Horario no encontrado: " + horarioId));
        CopiaHorarioResultado resultado = new CopiaHorarioResultado();
        for (Long funcionarioId : new LinkedHashSet<>(funcionarioIds)) {
            Funcionario f = funcionarioId == null ? null : funcionarioRepository.findById(funcionarioId).orElse(null);
            if (f == null) continue;
            String nombre = f.getPersona() != null ? f.getPersona().getNombre() : "Funcionario " + f.getId();
            Usuario usuario = f.getPersona() != null ? usuarioService.findByPersonaId(f.getPersona().getId()) : null;
            if (usuario == null) {
                resultado.getErrores().add(nombre + ": no tiene usuario para marcar");
                continue;
            }
            List<Horario> suyos = repository.findByUsuarioIdOrderByIdDesc(usuario.getId());
            if (suyos.stream().anyMatch(h -> mismoHorario(h, origen))) {
                resultado.setCopiados(resultado.getCopiados() + 1);
                continue;
            }
            Horario copia = new Horario();
            copiarDatos(origen, copia);
            copia.setToleranciaMinutos(origen.getToleranciaMinutos());
            copia.setInicioDescanso(origen.getInicioDescanso());
            copia.setFinDescanso(origen.getFinDescanso());
            copia.setUsuario(usuario);
            copia = repository.save(copia);
            if (f.getHorario() == null) {
                f.setHorario(copia);
                funcionarioRepository.save(f);
            }
            resultado.setCopiados(resultado.getCopiados() + 1);
        }
        return resultado;
    }

    /**
     * Los horarios que rigen para el funcionario: los mismos que usa la marcacion (los propios
     * de su usuario, o el principal si todavia no tiene ninguno propio).
     */
    @Transactional(readOnly = true)
    public List<Horario> deFuncionario(Long funcionarioId) {
        Funcionario f = funcionarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado: " + funcionarioId));
        Usuario usuario = f.getPersona() != null ? usuarioService.findByPersonaId(f.getPersona().getId()) : null;
        return horarioResolver.horariosVigentes(usuario != null ? usuario.getId() : null, f);
    }

    /**
     * Crea un horario y se lo deja asignado al funcionario: queda como horario propio de su
     * usuario (la cuenta que marca, por la persona).
     */
    @Transactional
    public Funcionario agregarAFuncionario(Long funcionarioId, Horario horario) {
        Funcionario f = funcionarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado: " + funcionarioId));
        Usuario usuario = f.getPersona() != null ? usuarioService.findByPersonaId(f.getPersona().getId()) : null;
        if (usuario == null) {
            throw new GraphQLException("El funcionario no tiene usuario para marcar: no se le puede asignar horario");
        }
        validarDatos(horario);

        // Si todavia usa el principal compartido (sin horarios propios), al tener uno propio el
        // compartido dejaria de regir: se lo copia como propio para que lo conserve.
        boolean sinPropios = repository.findByUsuarioIdOrderByIdDesc(usuario.getId()).stream()
                .noneMatch(h -> h.getHoraEntrada() != null);
        Horario principal = f.getHorario();
        if (sinPropios && principal != null && principal.getHoraEntrada() != null) {
            Horario copia = new Horario();
            copiarDatos(principal, copia);
            copia.setToleranciaMinutos(principal.getToleranciaMinutos());
            copia.setInicioDescanso(principal.getInicioDescanso());
            copia.setFinDescanso(principal.getFinDescanso());
            copia.setUsuario(usuario);
            f.setHorario(repository.save(copia));
            funcionarioRepository.save(f);
        }

        Horario nuevo = new Horario();
        copiarDatos(horario, nuevo);
        nuevo.setUsuario(usuario);
        nuevo = repository.save(nuevo);
        if (f.getHorario() == null) {
            f.setHorario(nuevo);
            funcionarioRepository.save(f);
        }
        repository.flush();
        return f;
    }

    /**
     * Le quita un horario al funcionario. Si es propio de su usuario se borra; si es el
     * principal compartido de antes de #523, solo se lo desvincula y los demas lo conservan.
     */
    @Transactional
    public Funcionario quitarDeFuncionario(Long funcionarioId, Long horarioId) {
        Funcionario f = funcionarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new GraphQLException("Funcionario no encontrado: " + funcionarioId));
        Horario h = repository.findById(horarioId)
                .orElseThrow(() -> new GraphQLException("Horario no encontrado: " + horarioId));
        Usuario usuario = f.getPersona() != null ? usuarioService.findByPersonaId(f.getPersona().getId()) : null;
        boolean propio = usuario != null && h.getUsuario() != null && h.getUsuario().getId().equals(usuario.getId());
        if (propio && !compartido(h, f)) {
            eliminar(horarioId);
        } else if (f.getHorario() != null && f.getHorario().getId().equals(horarioId)) {
            f.setHorario(null);
            funcionarioRepository.save(f);
        } else if (propio) {
            // Propio pero tambien lo usan otros funcionarios: deja de ser de este usuario.
            h.setUsuario(null);
            repository.save(h);
        } else {
            throw new GraphQLException("Ese horario no está asignado a este funcionario");
        }
        repository.flush();
        return funcionarioRepository.findById(funcionarioId).orElse(f);
    }

    /** Horario por dia del funcionario, ej. "L-Mi Día 08:00-17:00 · J Madrugada 17:00-01:00". */
    @Transactional(readOnly = true)
    public String resumen(Funcionario funcionario) {
        Usuario usuario = funcionario.getPersona() != null
                ? usuarioService.findByPersonaId(funcionario.getPersona().getId())
                : null;
        return HorarioSemanal.resumen(
                horarioResolver.horariosVigentes(usuario != null ? usuario.getId() : null, funcionario));
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
     * funcionario.horario_id ya no decide nada (#523), pero lo leen pantallas viejas: apunta
     * al horario que cubre mas dias.
     */
    private Horario principal(List<Horario> horarios) {
        return horarios.stream()
                .max(Comparator.comparingInt(h -> HorarioSemanal.esGeneral(h) ? 7 : h.getDias().size()))
                .orElse(null);
    }

    private void quitar(Horario viejo, Funcionario propio, Horario reemplazo) {
        if (compartido(viejo, propio)) {
            // Lo usan otros funcionarios: se lo saca de este usuario sin borrarlo.
            viejo.setUsuario(null);
            repository.save(viejo);
            return;
        }
        for (Funcionario f : funcionarioRepository.findByHorarioId(viejo.getId())) {
            f.setHorario(reemplazo);
            funcionarioRepository.save(f);
        }
        repository.delete(viejo);
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

    private boolean compartido(Horario horario, Funcionario propio) {
        if (horario.getId() == null) return false;
        return funcionarioRepository.findByHorarioId(horario.getId()).stream()
                .anyMatch(f -> propio == null || !f.getId().equals(propio.getId()));
    }

    private Funcionario funcionarioDe(Usuario usuario) {
        if (usuario == null || usuario.getPersona() == null) return null;
        return funcionarioRepository.findByPersonaId(usuario.getPersona().getId());
    }
}
