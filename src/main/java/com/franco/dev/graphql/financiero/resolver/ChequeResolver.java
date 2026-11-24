package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.service.financiero.ChequeService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChequeResolver implements GraphQLResolver<Cheque> {

    @Autowired
    private ChequeService chequeService;

    @Autowired
    private PagoSolicitudDetalleRepository pagoDetalleRepository;

    /**
     * Evento de pago del que salió el cheque, o null si se emitió suelto (issue #601). El vínculo vive en
     * {@code pago_solicitud_detalle.cheque_id}: el cheque no lo sabe.
     */
    public Long pagoId(Cheque cheque) {
        if (cheque == null || cheque.getId() == null) return null;
        return pagoDetalleRepository.findFirstByChequeIdOrderByIdAsc(cheque.getId())
                .map(PagoSolicitudDetalle::getPagoId).orElse(null);
    }

    /** «PAGO» si salió de un pago, «MANUAL» si se emitió suelto desde Cheques. */
    public String origen(Cheque cheque) {
        return pagoId(cheque) != null ? "PAGO" : "MANUAL";
    }
}
