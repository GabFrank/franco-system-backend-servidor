package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.service.financiero.ChequeService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PagoDetalleCuotaResolver implements GraphQLResolver<PagoDetalleCuota> {

    @Autowired
    private ChequeService chequeService;

    public Cheque cheque(PagoDetalleCuota pagoDetalleCuota) {
        return chequeService.findByPagoDetalleCuotaId(pagoDetalleCuota.getId());
    }
} 