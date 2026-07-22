package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.HoraExtra;
import com.franco.dev.domain.rrhh.enums.HoraExtraTipo;
import com.franco.dev.graphql.rrhh.input.HoraExtraInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.HoraExtraService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.stringToLocalDate;

@Component
public class HoraExtraGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private HoraExtraService service;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<HoraExtra> horaExtra(Long id) {
        return service.findById(id);
    }

    public List<HoraExtra> horasExtraPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<HoraExtra> horasExtraPorFuncionarioYRango(Long funcionarioId, String desde, String hasta) {
        return service.findByFuncionarioIdAndFechaBetween(
                funcionarioId,
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<HoraExtra> horasExtraPage(int page, int size, Long funcionarioId, String desde, String hasta,
                                         HoraExtraTipo tipo) {
        return service.findPage(funcionarioId, stringToLocalDate(desde), stringToLocalDate(hasta), tipo,
                PageRequest.of(page, size));
    }

    public HoraExtra saveHoraExtra(HoraExtraInput input) {
        HoraExtra e = input.getId() != null
                ? service.findById(input.getId()).orElse(new HoraExtra())
                : new HoraExtra();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        e.setJornadaId(input.getJornadaId());
        e.setSucursalId(input.getSucursalId());
        e.setMinutos(input.getMinutos());
        e.setTipo(input.getTipo());
        e.setRecargoPorcentaje(input.getRecargoPorcentaje());
        e.setMontoCalculado(input.getMontoCalculado());
        e.setOrigen(input.getOrigen());
        if (input.getAnulada() != null) e.setAnulada(input.getAnulada());
        if (input.getAutorizadoPorId() != null)
            e.setAutorizadoPor(usuarioService.findById(input.getAutorizadoPorId()).orElse(null));
        e.setObservacion(input.getObservacion());
        return service.save(e);
    }

    public HoraExtra anularHoraExtra(Long id) {
        return service.anular(id);
    }

    public Boolean deleteHoraExtra(Long id) {
        return service.deleteById(id);
    }
}
