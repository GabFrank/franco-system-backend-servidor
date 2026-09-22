package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PagoResolver implements GraphQLResolver<Pago> {

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    @Autowired
    private TesoreriaSecurityService tesoreriaSecurityService;

    @Autowired
    private RrhhSecurityService rrhhSecurityService;

    /**
     * Solicitudes del evento de pago. Se llega sin rol desde una solicitud de compra ({@code solicitudPago.pago}); un
     * pago anterior a #302 pudo mezclar compras con obligaciones de RRHH, que solo se muestran con rol de tesoreria o
     * de RRHH (issue #306).
     */
    public List<SolicitudPago> solicitudesPago(Pago pago) {
        List<SolicitudPago> solicitudes = solicitudPagoService.findByPagoId(pago.getId());
        if (solicitudes == null || solicitudes.stream().noneMatch(s -> s.getTipo() == TipoSolicitudPago.RRHH)) {
            return solicitudes;
        }
        if (tesoreriaSecurityService.hasAnyRole(TesoreriaSecurityService.TODOS)
                || rrhhSecurityService.hasAnyRole(RrhhSecurityService.TODOS)) {
            return solicitudes;
        }
        return solicitudes.stream().filter(s -> s.getTipo() != TipoSolicitudPago.RRHH).collect(Collectors.toList());
    }

}

