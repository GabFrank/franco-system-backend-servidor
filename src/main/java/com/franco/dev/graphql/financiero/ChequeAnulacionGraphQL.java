package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.service.financiero.AnulacionPagoRrhhService;
import com.franco.dev.service.financiero.ChequeAnuladoTicketService;
import com.franco.dev.service.financiero.ChequeGestionService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lo que el desktop necesita alrededor de la anulación de un cheque (#807): qué cheques anula y su comprobante. */
@Component
@AllArgsConstructor
public class ChequeAnulacionGraphQL implements GraphQLQueryResolver {

    private final ChequeGestionService chequeGestionService;
    private final AnulacionPagoRrhhService anulacionPagoRrhhService;
    private final ChequeAnuladoTicketService ticketService;
    private final TesoreriaSecurityService seg;
    private final RrhhSecurityService rrhhSeg;

    /**
     * Los cheques que va a anular la anulación del pago, de la liquidación o del finiquito (uno solo de los tres).
     * Pide el mismo rol que la mutation que sigue, para no trabar una anulación que el usuario sí puede hacer.
     */
    public List<Cheque> chequesAAnular(Long pagoId, Long liquidacionId, Long liquidacionFinalId) {
        boolean deRrhh = pagoId == null && (liquidacionId != null || liquidacionFinalId != null);
        // El rol va antes que los argumentos: sin rol, la respuesta es «no autorizado», no un error de uso.
        if (deRrhh) rrhhSeg.requireAnyRole(RrhhSecurityService.PAGAR);
        else seg.requirePagarCppCaja();
        int indicados = (pagoId != null ? 1 : 0) + (liquidacionId != null ? 1 : 0) + (liquidacionFinalId != null ? 1 : 0);
        if (indicados != 1) throw new GraphQLException("Indicá un pago, una liquidación o un finiquito.");
        if (pagoId != null) {
            // El rol acotado no anula pagos con cheque (PagoProveedorService lo rechaza): no hay cheques que mostrarle.
            if (seg.pagaAcotado()) return new ArrayList<>();
            return chequeGestionService.chequesAAnular(Collections.singletonList(pagoId));
        }
        List<Long> pagos = liquidacionId != null
                ? anulacionPagoRrhhService.pagosAAnularDeLiquidacion(liquidacionId)
                : anulacionPagoRrhhService.pagosAAnularDeFiniquito(liquidacionFinalId);
        return chequeGestionService.chequesAAnular(pagos);
    }

    /** Ticket ESC/POS (base64) del comprobante de anulación. 58 o 80 mm. */
    public String imprimirChequeAnulado(Long chequeId, Integer anchoMm) {
        seg.requireVer();
        return ticketService.ticketBase64(chequeId, anchoMm);
    }
}
