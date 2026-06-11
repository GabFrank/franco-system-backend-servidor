package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.ConfiguracionTransferencia;
import com.franco.dev.graphql.operaciones.input.ConfiguracionTransferenciaInput;
import com.franco.dev.service.operaciones.ConfiguracionTransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
public class ConfiguracionTransferenciaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final ConfiguracionTransferenciaService service;
    private final UsuarioService usuarioService;

    public ConfiguracionTransferencia configuracionTransferencia() {
        return service.findOrCreate();
    }

    public ConfiguracionTransferencia saveConfiguracionTransferencia(ConfiguracionTransferenciaInput input) {
        ConfiguracionTransferencia config = service.findOrCreate();
        if (input.getPermitirStockNegativo() != null) {
            config.setPermitirStockNegativo(input.getPermitirStockNegativo());
        }
        if (input.getUsuarioId() != null) {
            config.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        config.setModificadoEn(LocalDateTime.now());
        return service.save(config);
    }
}
