package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.rrhh.AsistenciaService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import com.franco.dev.service.rrhh.dto.AsistenciaResumenDto;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AsistenciaGraphQL implements GraphQLQueryResolver {

    @Autowired
    private AsistenciaService service;

    @Autowired
    private RrhhSecurityService seg;

    public AsistenciaResumenDto asistenciaFuncionario(Long funcionarioId, int anho, int mes) {
        seg.requireVer();
        return service.calcular(funcionarioId, anho, mes);
    }
}
