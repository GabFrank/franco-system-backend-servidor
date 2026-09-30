package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.service.rrhh.ValeService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ValeResolver implements GraphQLResolver<Vale> {

    @Autowired
    private ValeService valeService;

    /** Lo que falta descontar del vale (suma de cuotas pendientes si va en cuotas). */
    public BigDecimal saldoPendiente(Vale vale) {
        return valeService.saldoPendiente(vale);
    }
}
