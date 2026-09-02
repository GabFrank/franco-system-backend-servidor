package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.Dia;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.service.personas.FuncionarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class HorarioResolver {

    private final FuncionarioService funcionarioService;
    private final HorarioRepository horarioRepository;

    public Horario resolver(Marcacion marcacion, LocalDateTime fechaReferencia) {
        Dia diaSemana = mapToDia(fechaReferencia.getDayOfWeek());

        return getFuncionario(marcacion)
                .map(f -> {
                    Horario horario = f.getHorario();
                    if (horario == null) return null;
                    if (cumpleCondiciones(horario, diaSemana)) {
                        return horario;
                    }
                    return buscarAlternativo(marcacion.getUsuario().getId(), diaSemana);
                })
                .orElseGet(() -> buscarPorUsuarioYDia(marcacion, diaSemana));
    }

    private Dia mapToDia(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return Dia.LUNES;
            case TUESDAY: return Dia.MARTES;
            case WEDNESDAY: return Dia.MIERCOLES;
            case THURSDAY: return Dia.JUEVES;
            case FRIDAY: return Dia.VIERNES;
            case SATURDAY: return Dia.SABADO;
            case SUNDAY: return Dia.DOMINGO;
            default: return null;
        }
    }

    private Optional<Funcionario> getFuncionario(Marcacion marcacion) {
        if (marcacion.getUsuario() == null) return Optional.empty();
        if (marcacion.getUsuario().getPersona() != null) {
            return Optional.ofNullable(funcionarioService.findByPersonaId(marcacion.getUsuario().getPersona().getId()));
        }
        return Optional.ofNullable(funcionarioService.findByUsuarioId(marcacion.getUsuario().getId()));
    }

    /** Un horario sin dias cargados no discrimina: rige todos los dias. */
    private boolean cumpleCondiciones(Horario horario, Dia diaSemana) {
        if (horario.getDias() == null || horario.getDias().isEmpty()) return true;
        return horario.getDias().contains(diaSemana) || horario.getDias().contains(Dia.TODOS);
    }

    /**
     * Otro horario del usuario que si rija ese dia. Si ninguno rige, devuelve null a
     * proposito: la jornada queda sin horario y se evalua contra la jornada estandar de
     * 8 h. Antes se devolvia "cualquier horario del usuario", y eso le encajaba el turno
     * nocturno a quien trabajaba un fin de semana en horario de dia, inventando horas
     * extras que nadie hizo.
     */
    private Horario buscarAlternativo(Long usuarioId, Dia diaSemana) {
        List<Horario> horariosUsuario = horarioRepository.findByUsuarioIdOrderByIdDesc(usuarioId);
        if (horariosUsuario == null || horariosUsuario.isEmpty()) return null;

        return horariosUsuario.stream()
                .filter(h -> h.getHoraEntrada() != null && cumpleCondiciones(h, diaSemana))
                .findFirst()
                .orElse(null);
    }

    private Horario buscarPorUsuarioYDia(Marcacion marcacion, Dia diaSemana) {
        if (marcacion.getUsuario() != null && diaSemana != null) {
            return horarioRepository.findByUsuarioIdAndDia(marcacion.getUsuario().getId(), diaSemana);
        }
        return null;
    }
}
