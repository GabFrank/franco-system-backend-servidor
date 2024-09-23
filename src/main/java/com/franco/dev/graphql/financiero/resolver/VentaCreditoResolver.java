package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.kickstart.tools.GraphQLResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VentaCreditoResolver implements GraphQLResolver<VentaCredito> {

    private final Logger log = LoggerFactory.getLogger(VentaCreditoResolver.class);

    @Autowired
    private SucursalService sucursalService;

    public Sucursal sucursal(VentaCredito e) {
        return sucursalService.findById(e.getSucursalId()).orElse(null);
    }

}
