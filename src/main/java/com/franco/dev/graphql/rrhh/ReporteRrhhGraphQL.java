package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.rrhh.ReporteRrhhService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReporteRrhhGraphQL implements GraphQLQueryResolver {

    @Autowired
    private ReporteRrhhService service;

    /** Nómina del mes en PDF (base64). */
    public String reporteNominaMes(String periodo) {
        return service.nominaMesBase64(periodo);
    }

    /** Resumen IPS en PDF (base64). */
    public String reporteResumenIps(String periodo) {
        return service.resumenIpsBase64(periodo);
    }

    /** Recibo de finiquito (liquidación final) en PDF (base64). */
    public String imprimirReciboFinal(Long id) {
        return service.finiquitoBase64(id);
    }

    /** Vales pendientes en PDF (base64). */
    public String reporteValesPendientes() {
        return service.reporteValesPendientesBase64();
    }

    /** Préstamos activos en PDF (base64). */
    public String reportePrestamosActivos() {
        return service.reportePrestamosActivosBase64();
    }

    /** Aguinaldo del año en PDF (base64). */
    public String reporteAguinaldoAnual(Integer anio) {
        return service.reporteAguinaldoAnualBase64(anio);
    }
}
