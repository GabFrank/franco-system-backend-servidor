package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.JornadaNovedad;
import com.franco.dev.graphql.rrhh.input.JornadaNovedadInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.JornadaNovedadService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class JornadaNovedadGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private JornadaNovedadService service;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<JornadaNovedad> jornadaNovedad(Long id) {
        return service.findById(id);
    }

    public List<JornadaNovedad> jornadaNovedadesPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<JornadaNovedad> jornadaNovedadesPorFuncionarioYRango(Long funcionarioId, String desde, String hasta) {
        return service.findByFuncionarioIdAndFechaBetween(
                funcionarioId,
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null);
    }

    public JornadaNovedad saveJornadaNovedad(JornadaNovedadInput input) {
        JornadaNovedad e = input.getId() != null
                ? service.findById(input.getId()).orElse(new JornadaNovedad())
                : new JornadaNovedad();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        e.setTipo(input.getTipo());
        e.setJornadaId(input.getJornadaId());
        e.setSucursalId(input.getSucursalId());
        e.setObservacion(input.getObservacion());
        if (input.getRegistradoPorId() != null)
            e.setRegistradoPor(usuarioService.findById(input.getRegistradoPorId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteJornadaNovedad(Long id) {
        return service.deleteById(id);
    }
}
