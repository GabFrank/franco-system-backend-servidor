package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.service.financiero.AjusteDeSaldoService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ajuste de una caja mayor por conteo (issue #376). El de saldo bancario sigue en CuentaBancariaGraphQL. */
@Component
@RequiredArgsConstructor
public class AjusteDeSaldoGraphQL implements GraphQLMutationResolver {

    private final AjusteDeSaldoService service;
    private final TesoreriaSecurityService seg;

    /**
     * El desktop manda el saldo que vio y lo que contó; la diferencia la calcula el central. No se
     * carga nada acá: el servicio toma el saldo con lock dentro de su transacción.
     */
    public MovimientoCajaVirtual ajustarCajaVirtualPorConteo(Long cajaVirtualId, Long monedaId,
                                                             Double saldoEsperado, Double contado) {
        seg.requireGestionar();
        return service.ajustarCajaPorConteo(cajaVirtualId, monedaId, saldoEsperado, contado, seg.currentUsuario());
    }
}
