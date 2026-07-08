package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.service.rrhh.AguinaldoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AguinaldoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private AguinaldoService service;

    public Optional<Aguinaldo> aguinaldo(Long id) {
        return service.findById(id);
    }

    public List<Aguinaldo> aguinaldosPorAnio(Integer anio) {
        return service.findByAnio(anio);
    }

    public List<Aguinaldo> aguinaldosPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public Integer calcularAguinaldosAnio(Integer anio) {
        return service.calcularAguinaldosAnio(anio);
    }

    public Aguinaldo aprobarAguinaldo(Long id) {
        return service.aprobar(id);
    }
}
