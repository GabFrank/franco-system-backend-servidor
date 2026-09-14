package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.service.financiero.ReporteMovimientosCajaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reportes PDF (base64) de los movimientos del dashboard de caja mayor. Reciben los mismos filtros
 * que las queries de la tabla ({@code movimientosCajaVirtualFilter} / {@code movimientosBancarios})
 * y exigen lo mismo que ver la caja: un rol de tesorería y acceso de lectura a esa caja.
 */
@Component
@AllArgsConstructor
public class ReporteMovimientosCajaGraphQL implements GraphQLQueryResolver {

    private final ReporteMovimientosCajaService service;
    private final TesoreriaSecurityService seg;

    public String imprimirReporteMovimientosCajaVirtual(Long cajaVirtualId, String desde, String fin,
                                                        CajaVirtualTipoMovimiento tipo, Long monedaId,
                                                        Boolean soloActivos) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
        return service.reporteCajaVirtual(cajaVirtualId, desde, fin, tipo, monedaId, Boolean.TRUE.equals(soloActivos));
    }

    public String imprimirReporteMovimientosBancarios(Long cajaVirtualId, Long cuentaBancariaId, String desde,
                                                      String fin, String tipo, Boolean soloActivos) {
        seg.requireVer();
        seg.requireLecturaCaja(cajaVirtualId);
        return service.reporteBancario(cajaVirtualId, cuentaBancariaId, desde, fin, tipo, Boolean.TRUE.equals(soloActivos));
    }
}
