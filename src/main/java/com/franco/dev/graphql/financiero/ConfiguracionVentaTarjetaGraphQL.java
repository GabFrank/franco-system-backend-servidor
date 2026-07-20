package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.graphql.financiero.input.ConfiguracionVentaTarjetaInput;
import com.franco.dev.service.financiero.ConfiguracionVentaTarjetaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
public class ConfiguracionVentaTarjetaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final ConfiguracionVentaTarjetaService service;
    private final UsuarioService usuarioService;

    public ConfiguracionVentaTarjeta configuracionVentaTarjeta() {
        return service.findOrCreate();
    }

    public ConfiguracionVentaTarjeta saveConfiguracionVentaTarjeta(ConfiguracionVentaTarjetaInput input) {
        ConfiguracionVentaTarjeta config = service.findOrCreate();
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
