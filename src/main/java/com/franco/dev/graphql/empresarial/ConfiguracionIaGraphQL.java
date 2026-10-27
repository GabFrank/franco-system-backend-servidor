package com.franco.dev.graphql.empresarial;

import com.franco.dev.service.empresarial.ConfiguracionIa;
import com.franco.dev.service.empresarial.ConfiguracionSistemaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracionIaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConfiguracionSistemaService service;

    public ConfiguracionIa configuracionIa() {
        service.exigirAdmin();
        return service.configuracionIa();
    }

    public ConfiguracionIa guardarConfiguracionIa(String apiKey, String modelo, String promptAdicional,
                                                  String proveedor, String claudeApiKey, String claudeModelo) {
        return service.guardarConfiguracionIa(apiKey, modelo, promptAdicional, proveedor, claudeApiKey, claudeModelo);
    }

    public String probarConexionIa(String proveedor) {
        return service.probarConexionIa(proveedor);
    }
}
