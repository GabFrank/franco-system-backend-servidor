package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

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

    private static final String ATRIBUTO = CuentaBancariaResolver.class.getName() + ".ocultarSaldo";

    @Autowired
    private TesoreriaSecurityService seg;

    public BigDecimal saldo(CuentaBancaria cuenta) {
        return ocultarSaldo() ? null : cuenta.getSaldo();
    }

    public BigDecimal saldoReservado(CuentaBancaria cuenta) {
        return ocultarSaldo() ? null : cuenta.getSaldoReservado();
    }

    /**
     * Se resuelve una vez por request: un listado de cuentas pide los dos campos por cada fila, y cada
     * consulta de rol va a la base. Sin request (un test, un hilo aparte) se calcula cada vez.
     */
    private boolean ocultarSaldo() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null) return seg.esSoloCaja();
        Object guardado = request.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
        if (guardado instanceof Boolean) return (Boolean) guardado;
        boolean ocultar = seg.esSoloCaja();
        request.setAttribute(ATRIBUTO, ocultar, RequestAttributes.SCOPE_REQUEST);
        return ocultar;
    }
}
