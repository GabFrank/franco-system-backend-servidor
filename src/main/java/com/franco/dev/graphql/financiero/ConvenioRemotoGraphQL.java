package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.dto.ConvenioRemoto;
import com.franco.dev.service.financiero.ConvenioRemotoService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConvenioRemotoGraphQL implements GraphQLQueryResolver {

    @Autowired
    private ConvenioRemotoService service;

    /** Sin argumentos a propósito: el CI sale de la sesión, nunca del cliente. */
    public ConvenioRemoto convenioOtraEmpresa() {
        return service.deLaPersonaEnSesion();
    }
}
