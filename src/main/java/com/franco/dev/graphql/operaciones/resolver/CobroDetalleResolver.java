package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.service.operaciones.CobroDetalleService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CobroDetalleResolver implements GraphQLResolver<CobroDetalle> {

    @Autowired
    private CobroDetalleService cobroDetalleService;

    public Long sucursalId(CobroDetalle c){
        return c.getSucursal().getId();
    }
}
