package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.service.financiero.ChequeDashboardService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

/** Queries del dashboard de cheques (todo por fecha de pago). */
@Component
@AllArgsConstructor
public class ChequeDashboardGraphQL implements GraphQLQueryResolver {

    private final ChequeDashboardService service;
    private final TesoreriaSecurityService seg;

    public List<Cheque> chequesDashboard(String desde, String hasta, Long cuentaBancariaId, Long chequeraId, EstadoCheque estado) {
        seg.requireVer();
        return service.filtrar(parse(desde), parse(hasta), cuentaBancariaId, chequeraId, estado);
    }

    public List<ChequeDashboardService.ResumenDia> chequesResumenPorDia(String desde, String hasta, Long cuentaBancariaId, Long chequeraId, EstadoCheque estado) {
        seg.requireVer();
        return service.resumenPorDia(parse(desde), parse(hasta), cuentaBancariaId, chequeraId, estado);
    }

    public List<ChequeDashboardService.SaldoChequera> chequesSaldosPorChequera(String hasta, EstadoCheque estado) {
        seg.requireVer();
        return service.saldosPorChequera(parse(hasta), estado);
    }

    private LocalDateTime parse(String s) {
        return (s != null && !s.isEmpty()) ? stringToDate(s) : null;
    }
}
