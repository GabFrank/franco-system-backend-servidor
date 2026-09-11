package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.service.rrhh.BonoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BonoResolver implements GraphQLResolver<Bono> {

    @Autowired
    private BonoService bonoService;

    /**
     * Resolver para el campo editable del tipo Bono.
     * GraphQL automáticamente llama a este método cuando se solicita el campo.
     */
    public Boolean editable(Bono bono) {
        return bonoService.motivoNoEditable(bono) == null;
    }

    /**
     * Resolver para el campo motivoNoEditable del tipo Bono.
     */
    public String motivoNoEditable(Bono bono) {
        return bonoService.motivoNoEditable(bono);
    }
}
