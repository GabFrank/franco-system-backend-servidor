package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.service.operaciones.DevolucionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetiroDevolucionResolver implements GraphQLResolver<RetiroDevolucion> {

    @Autowired
    private DevolucionService devolucionService;

    public List<Devolucion> devoluciones(RetiroDevolucion r) {
        return devolucionService.findByRetiroId(r.getId());
    }
}
