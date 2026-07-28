package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.rrhh.DashboardRrhhService;
import com.franco.dev.service.rrhh.dto.DashboardRrhhKpisDto;
import com.franco.dev.service.rrhh.dto.RrhhCumpleanosDto;
import com.franco.dev.service.rrhh.dto.RrhhIncompletosPageDto;
import com.franco.dev.service.rrhh.dto.RrhhRankingItemDto;
import com.franco.dev.service.rrhh.dto.RrhhSeriePuntoDto;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DashboardRrhhGraphQL implements GraphQLQueryResolver {

    @Autowired
    private DashboardRrhhService service;

    public DashboardRrhhKpisDto dashboardRrhhKpis(String periodo) {
        return service.getKpis(periodo);
    }

    public List<RrhhSeriePuntoDto> nominaSeriePorMes(String periodoInicio, String periodoFin) {
        return service.getNominaSeriePorMes(periodoInicio, periodoFin);
    }

    public List<RrhhRankingItemDto> rrhhTopExposicion(Integer limite) {
        return service.getTopExposicion(limite != null ? limite : 5);
    }

    public List<RrhhRankingItemDto> rrhhTopHorasExtra(String periodo, Integer limite) {
        return service.getTopHorasExtra(periodo, limite != null ? limite : 5);
    }

    public List<RrhhCumpleanosDto> rrhhCumpleanosDelMes(String periodo) {
        return service.getCumpleanosDelMes(periodo);
    }

    public RrhhIncompletosPageDto rrhhFuncionariosIncompletos(Integer page, Integer size) {
        return service.getFuncionariosIncompletos(page != null ? page : 0, size != null ? size : 10);
    }
}
