package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.service.rrhh.LiquidacionItemProgramadoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class LiquidacionItemProgramadoResolver implements GraphQLResolver<LiquidacionItemProgramado> {

    public Boolean vencido(LiquidacionItemProgramado p) {
        return LiquidacionItemProgramadoService.vencido(p, YearMonth.now());
    }
}
