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

    /**
     * Decide si se emiten documentos electronicos en toda la flota: solo ADMIN, igual que el boton
     * del desktop. TESORERIA GESTIONAR mueve cajas; no decide la emision de comprobantes.
     * El autor queda registrado desde la sesion, no desde el input.
     */
    public ConfiguracionFacturacion saveConfiguracionFacturacion(ConfiguracionFacturacionInput input) {
        seg.requireAnyRole(TesoreriaSecurityService.ADMIN);
        return service.guardar(input, seg.currentUsuario());
    }

    public Boolean deleteConfiguracionFacturacion(Long id) {
        seg.requireAnyRole(TesoreriaSecurityService.ADMIN);
        return service.eliminar(id);
    }
}
