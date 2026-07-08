package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.graphql.rrhh.input.BonoInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.BonoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class BonoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private BonoService service;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Bono> bono(Long id) {
        return service.findById(id);
    }

    public List<Bono> bonosPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public Bono saveBono(BonoInput input) {
        Bono e = input.getId() != null
                ? service.findById(input.getId()).orElse(new Bono())
                : new Bono();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        e.setTipo(input.getTipo());
        e.setMonto(input.getMonto());
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        e.setMotivo(input.getMotivo());
        if (input.getEsRecurrente() != null) e.setEsRecurrente(input.getEsRecurrente());
        e.setFrecuencia(input.getFrecuencia());
        if (input.getAutorizadoPorId() != null)
            e.setAutorizadoPor(usuarioService.findById(input.getAutorizadoPorId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Bono anularBono(Long id) {
        return service.anular(id);
    }
}
