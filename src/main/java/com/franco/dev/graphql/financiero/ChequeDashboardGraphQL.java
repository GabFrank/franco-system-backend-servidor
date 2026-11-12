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

    public List<Cheque> chequesDashboard(String desde, String hasta, Long cuentaBancariaId, Long chequeraId, EstadoCheque estado,
                                         String tipoFecha, List<EstadoCheque> estados, String texto, Long proveedorId) {
        seg.requireVer();
        return service.filtrar(filtro(desde, hasta, cuentaBancariaId, chequeraId, estado, tipoFecha, estados, texto, proveedorId));
    }

    public List<ChequeDashboardService.ResumenDia> chequesResumenPorDia(String desde, String hasta, Long cuentaBancariaId,
                                                                         Long chequeraId, EstadoCheque estado, String tipoFecha,
                                                                         List<EstadoCheque> estados, String texto, Long proveedorId) {
        seg.requireVer();
        return service.resumenPorDia(filtro(desde, hasta, cuentaBancariaId, chequeraId, estado, tipoFecha, estados, texto, proveedorId));
    }

    /** Un desktop anterior manda solo estado (o nada) y sin tipoFecha: queda el filtro por fecha de pago de siempre. */
    static ChequeDashboardService.Filtro filtro(String desde, String hasta, Long cuentaId, Long chequeraId, EstadoCheque estado,
                                                String tipoFecha, List<EstadoCheque> estados, String texto, Long proveedorId) {
        ChequeDashboardService.Filtro f = new ChequeDashboardService.Filtro();
        f.setDesde(parse(desde));
        f.setHasta(parse(hasta));
        f.setCuentaId(cuentaId);
        f.setChequeraId(chequeraId);
        f.setTipoFecha(ChequeDashboardService.TipoFecha.de(tipoFecha));
        if (estados != null && !estados.isEmpty()) f.getEstados().addAll(estados);
        else if (estado != null) f.getEstados().add(estado);
        f.setTexto(texto);
        f.setProveedorId(proveedorId);
        return f;
    }

    public List<ChequeDashboardService.SaldoChequera> chequesSaldosPorChequera(String hasta, EstadoCheque estado) {
        seg.requireVer();
        return service.saldosPorChequera(parse(hasta), estado);
    }

    private static LocalDateTime parse(String s) {
        return (s != null && !s.isEmpty()) ? stringToDate(s) : null;
    }
}
