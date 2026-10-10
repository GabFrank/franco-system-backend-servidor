package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.ControlStockNegativo;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlStockNegativoResolver implements GraphQLResolver<ControlStockNegativo> {

    @Autowired
    private SucursalService sucursalService;

    public Sucursal sucursal(ControlStockNegativo c) {
        return c.getSucursalId() != null ? sucursalService.findById(c.getSucursalId()).orElse(null) : null;
    }
}
