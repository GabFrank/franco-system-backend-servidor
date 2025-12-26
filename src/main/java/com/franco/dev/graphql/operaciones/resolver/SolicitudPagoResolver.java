package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class SolicitudPagoResolver implements GraphQLResolver<SolicitudPago> {

    // Simple getter - no additional logic needed since it's a direct relationship
    public Pago pago(SolicitudPago solicitudPago) {
        return solicitudPago.getPago();
    }

}

