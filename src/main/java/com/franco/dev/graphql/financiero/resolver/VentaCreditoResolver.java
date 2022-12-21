package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.TipoGastoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VentaCreditoResolver implements GraphQLResolver<VentaCredito> {

    @Autowired
    private SucursalService sucursalService;

    public Sucursal sucursal(VentaCredito e){
        return sucursalService.findById(e.getSucursalId()).orElse(null);
    }

}
