package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.service.operaciones.PagoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PagoResolver implements GraphQLResolver<Pago> {

    @Autowired
    private PagoService pagoService;

}

