package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturaConVenta;
import com.franco.dev.graphql.financiero.input.ConfiguracionFacturaConVentaInput;
import com.franco.dev.service.financiero.ConfiguracionFacturaConVentaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
public class ConfiguracionFacturaConVentaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final ConfiguracionFacturaConVentaService service;
    private final UsuarioService usuarioService;

    public ConfiguracionFacturaConVenta configuracionFacturaConVenta() {
        return service.findOrCreate();
    }

    public ConfiguracionFacturaConVenta saveConfiguracionFacturaConVenta(ConfiguracionFacturaConVentaInput input) {
        ConfiguracionFacturaConVenta config = service.findOrCreate();
        if (input.getHabilitado() != null) {
            config.setHabilitado(input.getHabilitado());
        }
        if (input.getUsuarioId() != null) {
            config.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        config.setModificadoEn(LocalDateTime.now());
        return service.save(config);
    }
}
