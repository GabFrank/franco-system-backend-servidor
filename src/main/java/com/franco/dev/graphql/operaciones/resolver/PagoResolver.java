package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PagoResolver implements GraphQLResolver<Pago> {

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    public List<SolicitudPago> solicitudesPago(Pago pago) {
        return solicitudPagoService.findByPagoId(pago.getId());
    }

}

