package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.service.personas.FuncionarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HorarioResolver {

    private final FuncionarioService funcionarioService;
    private final HorarioRepository horarioRepository;

    /**
     * Horario de la jornada (#523): entre los que rigen el dia de {@code fechaReferencia}, el
     * de entrada mas cercana a su hora. Si no rige ninguno devuelve null y la jornada se mide
     * contra la estandar de 8 h. Ver {@link HorarioSemanal}.
     */
    public Horario resolver(Marcacion marcacion, LocalDateTime fechaReferencia) {
        if (marcacion.getUsuario() == null || marcacion.getUsuario().getId() == null) return null;
        List<Horario> horarios = horariosVigentes(marcacion.getUsuario().getId(), getFuncionario(marcacion));
        return HorarioSemanal.elegir(horarios, HorarioSemanal.dia(fechaReferencia.getDayOfWeek()),
                fechaReferencia.toLocalTime());
    }

    /**
     * Los horarios de la cuenta que marca. Desde #523 la fuente es una sola: los horarios
     * propios del usuario (horario.usuario_id). funcionario.horario_id queda solo como
     * respaldo de quien todavia no tiene ninguno propio; antes ganaba siempre y, como casi
     * todos eran de TODOS, tapaba los horarios de un dia puntual.
     */
    public List<Horario> horariosVigentes(Long usuarioId, Funcionario funcionario) {
        List<Horario> propios = usuarioId == null ? Collections.emptyList()
                : horarioRepository.findByUsuarioIdOrderByIdDesc(usuarioId).stream()
                        .filter(h -> h.getHoraEntrada() != null)
                        .collect(Collectors.toList());
        if (!propios.isEmpty()) return propios;
        if (funcionario != null && funcionario.getHorario() != null
                && funcionario.getHorario().getHoraEntrada() != null) {
            return Collections.singletonList(funcionario.getHorario());
        }
        return Collections.emptyList();
    }

    /**
     * Por la persona de la cuenta. No por funcionario.usuario_id: esa columna es "creado por"
     * y devolvia el funcionario de otro.
     */
    private Funcionario getFuncionario(Marcacion marcacion) {
        if (marcacion.getUsuario().getPersona() == null) return null;
        return funcionarioService.findByPersonaId(marcacion.getUsuario().getPersona().getId());
    }
}
