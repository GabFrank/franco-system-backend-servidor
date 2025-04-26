package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FacturaLegalResolver implements GraphQLResolver<FacturaLegal> {

    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private SucursalService sucursalService;

    public List<FacturaLegalItem> facturaLegalItemList(FacturaLegal e) {
        return facturaLegalItemService.findByFacturaLegalId(e.getId(), e.getSucursalId());
    }
    public Sucursal sucursal(FacturaLegal e){
        return sucursalService.findById(e.getSucursalId()).orElse(null);
    }
}
