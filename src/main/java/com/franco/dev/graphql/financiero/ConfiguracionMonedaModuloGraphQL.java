package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.graphql.financiero.input.ConfiguracionMonedaModuloInput;
import com.franco.dev.service.financiero.ConfiguracionMonedaModuloService;
import com.franco.dev.service.financiero.MonedaModuloLector;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Moneda por modulo (V258.5). Leer la moneda de un modulo no pide rol: la necesita cualquier
 * pantalla que cree un documento. Administrar la configuracion pide CONFIGURAR MONEDA (o ADMIN).
 */
@Component
@AllArgsConstructor
public class ConfiguracionMonedaModuloGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final ConfiguracionMonedaModuloService service;
    private final MonedaModuloLector lector;
    private final TesoreriaSecurityService seg;

    public Moneda monedaDeModulo(ModuloMoneda modulo, Long sucursalId) {
        return lector.resolver(modulo, sucursalId);
    }

    public Moneda monedaEmpresa() {
        return lector.empresa();
    }

    public List<ConfiguracionMonedaModulo> configuracionesMonedaModulo(ModuloMoneda modulo) {
        seg.requireAnyRole(TesoreriaSecurityService.CONFIGURAR_MONEDA);
        return service.listar(modulo);
    }

    /** El autor sale de la sesion, no del input. */
    public ConfiguracionMonedaModulo saveConfiguracionMonedaModulo(ConfiguracionMonedaModuloInput input) {
        seg.requireAnyRole(TesoreriaSecurityService.CONFIGURAR_MONEDA);
        return service.crear(input, seg.currentUsuario());
    }

    public ConfiguracionMonedaModulo setActivoConfiguracionMonedaModulo(Long id, Boolean activo) {
        seg.requireAnyRole(TesoreriaSecurityService.CONFIGURAR_MONEDA);
        return service.setActivo(id, activo);
    }
}
