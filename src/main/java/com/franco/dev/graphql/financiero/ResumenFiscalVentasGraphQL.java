package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.dto.ResumenFiscalVentas;
import com.franco.dev.service.financiero.FacturacionSecurityService;
import com.franco.dev.service.financiero.ReporteResumenFiscalVentasService;
import com.franco.dev.service.financiero.ResumenFiscalVentasService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resumen fiscal de ventas del mes (Reportes y Analisis): los datos para la pantalla y el PDF
 * para el contador. Lo pueden ver facturacion y analisis contable (o ADMIN).
 */
@Component
@RequiredArgsConstructor
public class ResumenFiscalVentasGraphQL implements GraphQLQueryResolver {

    static final String[] ROLES = {
            FacturacionSecurityService.VER, FacturacionSecurityService.EMITIR, "ANALISIS CONTABLE"
    };

    private final ResumenFiscalVentasService service;
    private final ReporteResumenFiscalVentasService reporteService;
    private final FacturacionSecurityService seg;

    public ResumenFiscalVentas resumenFiscalVentas(Integer anio, Integer mes, List<Long> sucIds) {
        seg.requireAnyRole(ROLES);
        return service.resumen(anio, mes, sucIds);
    }

    public String imprimirResumenFiscalVentas(Integer anio, Integer mes, List<Long> sucIds) {
        seg.requireAnyRole(ROLES);
        return reporteService.pdf(anio, mes, sucIds);
    }
}
