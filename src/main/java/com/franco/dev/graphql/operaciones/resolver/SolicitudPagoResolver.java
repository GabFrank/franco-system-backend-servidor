package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.service.operaciones.PagoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SolicitudPagoResolver implements GraphQLResolver<SolicitudPago> {

    @Autowired
    private PagoService pagoService;

    public Pago pago(SolicitudPago solicitudPago) {
        return pagoService.getRepository().findBySolicitudPagoId(solicitudPago.getId()) ;
    }

}

