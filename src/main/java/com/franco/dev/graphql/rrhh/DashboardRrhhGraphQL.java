package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.rrhh.DashboardRrhhService;
import com.franco.dev.service.rrhh.dto.DashboardRrhhKpisDto;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DashboardRrhhGraphQL implements GraphQLQueryResolver {

    @Autowired
    private DashboardRrhhService service;

    public DashboardRrhhKpisDto dashboardRrhhKpis(String periodo) {
        return service.getKpis(periodo);
    }
}
