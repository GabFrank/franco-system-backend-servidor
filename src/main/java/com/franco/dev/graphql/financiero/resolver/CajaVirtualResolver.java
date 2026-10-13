package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * El rol acotado (TESORERIA CAJA) no ve los saldos de una caja que no puede leer.
 *
 * <p>Las consultas de caja ya filtran por el ACL, pero una caja ajena llega igual como campo anidado: la
 * contraparte de una transferencia ({@code MovimientoCajaVirtual.cajaOrigen/cajaDestino}). Se cierra en el
 * campo para que valga por cualquier camino.</p>
 */
@Component
public class CajaVirtualResolver implements GraphQLResolver<CajaVirtual> {

    @Autowired
    private TesoreriaSecurityService seg;

    public Double saldoGs(CajaVirtual caja) {
        return oculto(caja) ? null : caja.getSaldoGs();
    }

    public Double saldoRs(CajaVirtual caja) {
        return oculto(caja) ? null : caja.getSaldoRs();
    }

    public Double saldoDs(CajaVirtual caja) {
        return oculto(caja) ? null : caja.getSaldoDs();
    }

    public Double limiteGs(CajaVirtual caja) {
        return oculto(caja) ? null : caja.getLimiteGs();
    }

    private boolean oculto(CajaVirtual caja) {
        return seg.esSoloCajaEnRequest() && !seg.puedeVerCajaEnRequest(caja.getId());
    }
}
