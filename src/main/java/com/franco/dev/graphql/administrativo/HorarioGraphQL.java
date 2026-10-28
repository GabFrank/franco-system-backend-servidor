package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.graphql.administrativo.input.HorarioInput;
import com.franco.dev.service.administrativo.HorarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Component
public class HorarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private HorarioService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Horario> horario(Long id) {
        return service.findById(id);
    }

    public List<Horario> horarios(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Horario> horariosPorUsuario(Long usuarioId) {
        return service.findByUsuarioId(usuarioId);
    }

    /** Los horarios que rigen para el funcionario, los mismos que usa la marcacion (#523). */
    public List<Horario> horariosPorFuncionario(Long funcionarioId) {
        return service.deFuncionario(funcionarioId);
    }

    /** Crea un horario y se lo deja asignado al funcionario (#523). */
    public com.franco.dev.domain.personas.Funcionario agregarHorarioFuncionario(Long funcionarioId, HorarioInput horario) {
        seg.requireAnyRole(com.franco.dev.service.rrhh.RrhhSecurityService.GESTIONAR, "EDITAR FUNCIONARIOS");
        return service.agregarAFuncionario(funcionarioId, toEntity(horario));
    }

    public com.franco.dev.domain.personas.Funcionario quitarHorarioFuncionario(Long funcionarioId, Long horarioId) {
        seg.requireAnyRole(com.franco.dev.service.rrhh.RrhhSecurityService.GESTIONAR, "EDITAR FUNCIONARIOS");
        return service.quitarDeFuncionario(funcionarioId, horarioId);
    }

    public Horario saveHorario(HorarioInput input) {
        seg.requireAnyRole(com.franco.dev.service.rrhh.RrhhSecurityService.GESTIONAR, "EDITAR FUNCIONARIOS");
        Horario e = toEntity(input);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.saveValidado(e);
    }

    /** Guarda la tabla de horarios del usuario entera (#523); los que no vienen se quitan. */
    public List<Horario> saveHorariosUsuario(Long usuarioId, List<HorarioInput> horarios) {
        seg.requireAnyRole(com.franco.dev.service.rrhh.RrhhSecurityService.GESTIONAR, "EDITAR FUNCIONARIOS");
        return service.saveDeUsuario(usuarioId,
                horarios.stream().map(this::toEntity).collect(java.util.stream.Collectors.toList()));
    }

    /** "Asignar a otros": copia el horario a cada funcionario como horario propio (#523). */
    public com.franco.dev.service.administrativo.dto.CopiaHorarioResultado copiarHorario(
            Long horarioId, List<Long> funcionarioIds) {
        seg.requireAnyRole(com.franco.dev.service.rrhh.RrhhSecurityService.GESTIONAR, "EDITAR FUNCIONARIOS");
        return service.copiar(horarioId, funcionarioIds);
    }

    public Boolean deleteHorario(Long id) {
        seg.requireAnyRole(com.franco.dev.service.rrhh.RrhhSecurityService.GESTIONAR, "EDITAR FUNCIONARIOS");
        return service.eliminar(id);
    }

    private Horario toEntity(HorarioInput input) {
        ModelMapper m = new ModelMapper();
        Horario e = m.map(input, Horario.class);
        e.setUsuario(null);
        e.setHoraEntrada(input.getHoraEntrada() != null ? LocalTime.parse(input.getHoraEntrada()) : null);
        e.setHoraSalida(input.getHoraSalida() != null ? LocalTime.parse(input.getHoraSalida()) : null);
        e.setInicioDescanso(input.getInicioDescanso() != null ? LocalTime.parse(input.getInicioDescanso()) : null);
        e.setFinDescanso(input.getFinDescanso() != null ? LocalTime.parse(input.getFinDescanso()) : null);
        if (input.getDias() != null) {
            e.setDias(input.getDias().stream().map(com.franco.dev.domain.administrativo.enums.Dia::valueOf)
                    .collect(java.util.stream.Collectors.toSet()));
        }
        e.setTurno(input.getTurno());
        return e;
    }
}
