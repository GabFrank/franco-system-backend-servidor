package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.graphql.financiero.input.ConfiguracionFacturacionInput;
import com.franco.dev.service.financiero.ConfiguracionFacturacionService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ABM de la politica de facturacion (issue filial #127). Solo la usa el dialogo de administracion
 * del desktop: el filial no pasa por aca, lee la copia replicada.
 */
@Component
@AllArgsConstructor
public class ConfiguracionFacturacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final ConfiguracionFacturacionService service;
    private final TesoreriaSecurityService seg;

    public List<ConfiguracionFacturacion> configuracionesFacturacion() {
        seg.requireVer();
        return service.listar();
    }

    /** Decide si se emiten documentos electronicos: exige TESORERIA GESTIONAR. */
    public ConfiguracionFacturacion saveConfiguracionFacturacion(ConfiguracionFacturacionInput input) {
        seg.requireGestionar();
        return service.guardar(input);
    }

    public Boolean deleteConfiguracionFacturacion(Long id) {
        seg.requireGestionar();
        return service.eliminar(id);
    }
}
