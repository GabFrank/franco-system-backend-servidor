package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.NotaCreditoDevolucion;
import com.franco.dev.domain.operaciones.NotaCreditoDevolucionItem;
import com.franco.dev.service.operaciones.NotaCreditoDevolucionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotaCreditoDevolucionResolver implements GraphQLResolver<NotaCreditoDevolucion> {

    @Autowired
    private NotaCreditoDevolucionService notaCreditoDevolucionService;

    public List<NotaCreditoDevolucionItem> items(NotaCreditoDevolucion nota) {
        return notaCreditoDevolucionService.itemsByNotaCredito(nota.getId());
    }
}
