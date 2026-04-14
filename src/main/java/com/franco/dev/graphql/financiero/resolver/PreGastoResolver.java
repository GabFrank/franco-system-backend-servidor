package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.PreGasto;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.service.financiero.PreGastoDetalleFinanzasService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Component
public class PreGastoResolver implements GraphQLResolver<PreGasto> {
    
    @Autowired
    private PreGastoDetalleFinanzasService preGastoDetalleFinanzasService;

    public List<PreGastoDetalleFinanzas> finanzas(PreGasto preGasto) {
        if (preGasto.getId() == null || preGasto.getSucursalId() == null) {
            return null;
        }
        return preGastoDetalleFinanzasService.findByPreGastoIdAndSucursalId(preGasto.getId(), preGasto.getSucursalId());
    }
}
