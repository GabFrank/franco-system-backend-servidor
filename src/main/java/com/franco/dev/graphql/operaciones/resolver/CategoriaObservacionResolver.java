package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.CategoriaObservacion;
import com.franco.dev.service.operaciones.CategoriaObservacionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
@Component
public class CategoriaObservacionResolver implements GraphQLResolver<CategoriaObservacion> {
    @Autowired
    private CategoriaObservacionService service;

    public List<CategoriaObservacion> categoriaObservacionSearch(Long id, String texto) {
        if (id != null && texto != null) {
            // Query with both filters
            return service.findByIdOrDesc(id, texto);
        } else {
           return Collections.emptyList();
        }
    }




}
