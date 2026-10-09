package com.franco.dev.graphql.rrhh;

import com.franco.dev.service.rrhh.ReporteRrhhService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reportes y recibos de RRHH. Todos exigen rol RRHH (issue #346): exponen nomina y datos
 * personales. El unico recibo con autoservicio es el de sueldo, en LiquidacionSueldoGraphQL.
 */
@Component
public class ReporteRrhhGraphQL implements GraphQLQueryResolver {

    @org.springframework.beans.factory.annotation.Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;


    @Autowired
    private ReporteRrhhService service;

    /** Nómina del mes en PDF (base64). */
    public String reporteNominaMes(String periodo, Long ciudadId, Boolean sinCiudad) {
        seg.requireVer();
        return service.nominaMesBase64(periodo, ciudadId, sinCiudad);
    }

    /** Resumen IPS en PDF (base64). */
    public String reporteResumenIps(String periodo) {
        seg.requireVer();
        return service.resumenIpsBase64(periodo);
    }

    /** Recibo de finiquito. anchoMm null = PDF A4; 58/80 = ticket; escpos=true = payload ESC/POS. */
    public String imprimirReciboFinal(Long id, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.finiquitoBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    /** Vales pendientes en PDF (base64). */
    public String reporteValesPendientes() {
        seg.requireVer();
        return service.reporteValesPendientesBase64();
    }

    /** Préstamos activos en PDF (base64). */
    public String reportePrestamosActivos() {
        seg.requireVer();
        return service.reportePrestamosActivosBase64();
    }

    /** Aguinaldo del año en PDF (base64). */
    public String reporteAguinaldoAnual(Integer anio) {
        seg.requireVer();
        return service.reporteAguinaldoAnualBase64(anio);
    }

    // ===== Recibos firmables por registro (anchoMm null = PDF A4; 58/80 = ticket) =====

    public String imprimirReciboVale(Long id, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.reciboValeBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    public String imprimirReciboPenalizacion(Long id, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.reciboPenalizacionBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    /** Acta de amonestacion. Solo PDF A4: dos firmas no entran en una termica. */
    public String imprimirActaAdvertencia(Long id) {
        // El acta lleva el motivo disciplinario del funcionario: no es un dato que deba
        // ver cualquier usuario autenticado.
        seg.requireVer();
        return service.actaAdvertenciaBase64(id);
    }

    public String imprimirReciboAguinaldo(Long id, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.reciboAguinaldoBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    public String imprimirReciboPrestamo(Long id, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.reciboPrestamoBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    public String imprimirReciboBono(Long id, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.reciboBonoBase64(id, anchoMm, Boolean.TRUE.equals(escpos));
    }

    /** Recibo de un item de liquidacion de sueldo (HABER: recibo; DESCUENTO: constancia). */
    public String imprimirReciboItemLiquidacion(Long itemId, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.reciboItemLiquidacionBase64(itemId, anchoMm, Boolean.TRUE.equals(escpos));
    }

    /** Recibo de un item de liquidacion final (finiquito). */
    public String imprimirReciboItemLiquidacionFinal(Long itemId, Integer anchoMm, Boolean escpos) {
        seg.requireVer();
        return service.reciboItemLiquidacionFinalBase64(itemId, anchoMm, Boolean.TRUE.equals(escpos));
    }
}
