package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * El saldo de una cuenta bancaria no se le muestra al rol acotado (TESORERIA CAJA): deposita a las
 * cuentas habilitadas para su caja, sin ver cuánto hay.
 *
 * <p>Va a nivel de campo porque {@code CuentaBancaria} cuelga de varios tipos (operación financiera,
 * configuración de caja, respuestas de mutations): cerrarlo en cada query deja afuera el próximo
 * camino que alguien agregue.</p>
 */
@Component
public class CuentaBancariaResolver implements GraphQLResolver<CuentaBancaria> {

    @Autowired
    private TesoreriaSecurityService seg;

    public BigDecimal saldo(CuentaBancaria cuenta) {
        return seg.esSoloCajaEnRequest() ? null : cuenta.getSaldo();
    }

    public BigDecimal saldoReservado(CuentaBancaria cuenta) {
        return seg.esSoloCajaEnRequest() ? null : cuenta.getSaldoReservado();
    }
}
