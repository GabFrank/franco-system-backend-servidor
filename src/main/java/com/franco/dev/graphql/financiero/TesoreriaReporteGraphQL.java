package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.TesoreriaReporteService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class TesoreriaReporteGraphQL implements GraphQLQueryResolver {

    private final TesoreriaReporteService service;
    private final TesoreriaSecurityService seg;

    public List<TesoreriaReporteService.SaldoPorMoneda> saldoConsolidadoTesoreria() {
        seg.requireVer();
        return service.saldoConsolidado();
    }
}
