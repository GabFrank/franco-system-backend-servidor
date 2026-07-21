package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.graphql.rrhh.input.JustificativoInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.JustificativoService;
import com.franco.dev.service.rrhh.TipoJustificativoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class JustificativoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private JustificativoService service;

    @Autowired
    private TipoJustificativoService tipoService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Justificativo> justificativo(Long id) {
        return service.findById(id);
    }

    public List<Justificativo> justificativosPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<Justificativo> justificativosPorFuncionarioYRango(Long funcionarioId, String desde, String hasta) {
        return service.findByFuncionarioIdAndFechaBetween(
                funcionarioId,
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null);
    }

    public Justificativo saveJustificativo(JustificativoInput input) {
        Justificativo e = input.getId() != null
                ? service.findById(input.getId()).orElse(new Justificativo())
                : new Justificativo();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        if (input.getTipoId() != null)
            e.setTipo(tipoService.findById(input.getTipoId()).orElse(null));
        e.setJornadaId(input.getJornadaId());
        e.setSucursalId(input.getSucursalId());
        e.setObservacion(input.getObservacion());
        if (input.getRegistradoPorId() != null)
            e.setRegistradoPor(usuarioService.findById(input.getRegistradoPorId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteJustificativo(Long id) {
        return service.deleteById(id);
    }
}
