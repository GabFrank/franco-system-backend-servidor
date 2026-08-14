package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.graphql.operaciones.input.DevolucionConfiguracionInput;
import com.franco.dev.service.operaciones.DevolucionConfiguracionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DevolucionConfiguracionGraphQL
        implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private DevolucionConfiguracionService service;

    public DevolucionConfiguracion devolucionConfiguracion() {
        return service.getConfiguracion();
    }

    public DevolucionConfiguracion guardarDevolucionConfiguracion(DevolucionConfiguracionInput input) {
        DevolucionConfiguracion entity = new ModelMapper().map(input, DevolucionConfiguracion.class);
        return service.guardar(entity);
    }
}
