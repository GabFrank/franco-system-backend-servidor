package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.RetiroDetalleService;
import graphql.kickstart.tools.GraphQLResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetiroResolver implements GraphQLResolver<Retiro> {

    private final Logger log = LoggerFactory.getLogger(RetiroResolver.class);

    @Autowired
    private RetiroDetalleService retiroDetalleService;

    @Autowired
    private SucursalService sucursalService;

    public List<RetiroDetalle> retiroDetalleList(Retiro e) {
        return retiroDetalleService.findByRetiroId(e.getId(), e.getSucursalId());
    }

    public Double retiroGs(Retiro e) {
        return retiroDetalleService.findByRetiroIdAndMonedaId(e.getId(), Long.valueOf(1), e.getSucursalId());
    }

    public Double retiroRs(Retiro e) {
        return retiroDetalleService.findByRetiroIdAndMonedaId(e.getId(), Long.valueOf(2), e.getSucursalId());
    }

    public Double retiroDs(Retiro e) {
        return retiroDetalleService.findByRetiroIdAndMonedaId(e.getId(), Long.valueOf(3), e.getSucursalId());
    }

    public Sucursal sucursal(Retiro e){
        return sucursalService.findById(e.getSucursalId()).orElse(null);
    }

}
