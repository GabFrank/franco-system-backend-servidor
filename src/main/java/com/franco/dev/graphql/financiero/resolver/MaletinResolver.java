package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.PdvCajaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MaletinResolver implements GraphQLResolver<Maletin> {

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MultiTenantService multiTenantService;

    public PdvCaja cajaActual(Maletin e) {
        PdvCaja aux = pdvCajaService.findLastByMaletinId(e.getId());
        return aux;
    }

}
