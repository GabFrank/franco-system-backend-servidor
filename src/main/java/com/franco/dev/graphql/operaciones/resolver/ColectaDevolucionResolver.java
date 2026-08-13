package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.ColectaDevolucion;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.service.operaciones.DevolucionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ColectaDevolucionResolver implements GraphQLResolver<ColectaDevolucion> {

    @Autowired
    private DevolucionService devolucionService;

    public List<Devolucion> devoluciones(ColectaDevolucion c) {
        return devolucionService.findByColectaId(c.getId());
    }
}
