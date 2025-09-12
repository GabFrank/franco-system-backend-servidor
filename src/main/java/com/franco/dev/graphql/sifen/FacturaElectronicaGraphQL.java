package com.franco.dev.graphql.sifen;

import com.franco.dev.domain.personas.FacturaElectronicaInput;
import com.franco.dev.domain.personas.GeneracionXmlResponse;
import com.franco.dev.service.sifen.service.FacturaElectronicaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FacturaElectronicaGraphQL implements GraphQLMutationResolver {

    @Autowired
    private FacturaElectronicaService facturaElectronicaService;

    public GeneracionXmlResponse generarXmlFactura(FacturaElectronicaInput input) {
        return facturaElectronicaService.generarXmlFactura(input);
    }
}
