package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import com.franco.dev.service.rrhh.dto.NominaMesItemDto;
import com.franco.dev.service.rrhh.dto.ResumenIpsItemDto;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reportes RRHH en PDF (base64) via JasperReports: nómina del mes y resumen IPS.
 * Patrón: cargar .jrxml → compilar → fill con params + datasource → export PDF.
 * Las plantillas usan solo fuente SansSerif (ver regla de reportes en CLAUDE.md).
 */
@Service
public class ReporteRrhhService {

    private final LiquidacionSueldoRepository liquidacionSueldoRepository;
    private final ConfiguracionRrhhService configuracionRrhhService;
    private final LiquidacionFinalService liquidacionFinalService;
    private final com.franco.dev.repository.rrhh.ValeRepository valeRepository;
    private final com.franco.dev.repository.rrhh.PrestamoRepository prestamoRepository;
    private final com.franco.dev.repository.rrhh.AguinaldoRepository aguinaldoRepository;
    private final com.franco.dev.repository.rrhh.PenalizacionRepository penalizacionRepository;
    private final com.franco.dev.repository.rrhh.BonoRepository bonoRepository;
    private final com.franco.dev.utilitarios.NumeroALetrasService numeroALetrasService;
    private final com.franco.dev.service.empresarial.ConfiguracionGeneralService configuracionGeneralService;
    private final DecimalFormat formato = new DecimalFormat("#,##0.##");
    private final DecimalFormat formatoGs = new DecimalFormat("#,##0");   // guaraníes sin decimales

    public ReporteRrhhService(LiquidacionSueldoRepository liquidacionSueldoRepository,
                              ConfiguracionRrhhService configuracionRrhhService,
                              LiquidacionFinalService liquidacionFinalService,
                              com.franco.dev.repository.rrhh.ValeRepository valeRepository,
                              com.franco.dev.repository.rrhh.PrestamoRepository prestamoRepository,
                              com.franco.dev.repository.rrhh.AguinaldoRepository aguinaldoRepository,
                              com.franco.dev.repository.rrhh.PenalizacionRepository penalizacionRepository,
                              com.franco.dev.repository.rrhh.BonoRepository bonoRepository,
                              com.franco.dev.utilitarios.NumeroALetrasService numeroALetrasService,
                              com.franco.dev.service.empresarial.ConfiguracionGeneralService configuracionGeneralService) {
        this.liquidacionSueldoRepository = liquidacionSueldoRepository;
        this.configuracionRrhhService = configuracionRrhhService;
        this.liquidacionFinalService = liquidacionFinalService;
        this.valeRepository = valeRepository;
        this.prestamoRepository = prestamoRepository;
        this.aguinaldoRepository = aguinaldoRepository;
        this.penalizacionRepository = penalizacionRepository;
        this.bonoRepository = bonoRepository;
        this.numeroALetrasService = numeroALetrasService;
        this.configuracionGeneralService = configuracionGeneralService;
    }

    /** Nómina del mes: liquidaciones aprobadas/pagadas del período. */
    @Transactional(readOnly = true)
    public String nominaMesBase64(String periodo) {
        validarPeriodo(periodo);
        List<NominaMesItemDto> filas = new ArrayList<>();
        BigDecimal totalNeto = BigDecimal.ZERO;
        for (LiquidacionSueldo l : liquidacionSueldoRepository.findByPeriodoOrderByIdAsc(periodo)) {
            if (l.getEstado() != LiquidacionSueldoEstado.APROBADA && l.getEstado() != LiquidacionSueldoEstado.PAGADA) {
                continue;
            }
            filas.add(new NominaMesItemDto(
                    nombreFuncionario(l.getFuncionario()),
                    formatear(l.getTotalHaberes()),
                    formatear(l.getTotalDescuentos()),
                    formatear(l.getTotalNeto())));
            if (l.getTotalNeto() != null) totalNeto = totalNeto.add(l.getTotalNeto());
        }
        if (filas.isEmpty()) filas.add(new NominaMesItemDto("SIN LIQUIDACIONES", "0", "0", "0"));

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", empresa(periodo));
        params.put("periodo", periodo);
        params.put("fecha", LocalDate.now().toString());
        params.put("totalNeto", formatear(totalNeto));

        return generar("reports/nomina-mes.jrxml", params, filas);
    }

    /** Resumen IPS: por cada liquidación del período, base salarial y aportes. */
    @Transactional(readOnly = true)
    public String resumenIpsBase64(String periodo) {
        validarPeriodo(periodo);
        BigDecimal pctFunc = configuracionRrhhService.getNumber("IPS_PORCENTAJE_FUNCIONARIO", new BigDecimal("9"));
        BigDecimal pctPatr = configuracionRrhhService.getNumber("IPS_PORCENTAJE_PATRONAL", new BigDecimal("16.5"));
        BigDecimal cien = new BigDecimal("100");

        List<ResumenIpsItemDto> filas = new ArrayList<>();
        BigDecimal totalFunc = BigDecimal.ZERO;
        BigDecimal totalPatr = BigDecimal.ZERO;
        for (LiquidacionSueldo l : liquidacionSueldoRepository.findByPeriodoOrderByIdAsc(periodo)) {
            if (l.getEstado() != LiquidacionSueldoEstado.APROBADA && l.getEstado() != LiquidacionSueldoEstado.PAGADA) {
                continue;
            }
            BigDecimal base = l.getSalarioBase() != null ? l.getSalarioBase() : BigDecimal.ZERO;
            BigDecimal aporteFunc = base.multiply(pctFunc).divide(cien, 2, RoundingMode.HALF_UP);
            BigDecimal aportePatr = base.multiply(pctPatr).divide(cien, 2, RoundingMode.HALF_UP);
            filas.add(new ResumenIpsItemDto(
                    nombreFuncionario(l.getFuncionario()),
                    formatear(base), formatear(aporteFunc), formatear(aportePatr)));
            totalFunc = totalFunc.add(aporteFunc);
            totalPatr = totalPatr.add(aportePatr);
        }
        if (filas.isEmpty()) filas.add(new ResumenIpsItemDto("SIN LIQUIDACIONES", "0", "0", "0"));

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", empresa(periodo));
        params.put("periodo", periodo);
        params.put("fecha", LocalDate.now().toString());
        params.put("porcentajeFuncionario", formato.format(pctFunc));
        params.put("porcentajePatronal", formato.format(pctPatr));
        params.put("totalFuncionario", formatear(totalFunc));
        params.put("totalPatronal", formatear(totalPatr));

        return generar("reports/resumen-ips.jrxml", params, filas);
    }

    /** Recibo de finiquito (liquidación final). anchoMm null = PDF A4; 58/80 = ticket (PDF o ESC/POS). */
    @Transactional(readOnly = true)
    public String finiquitoBase64(Long liquidacionFinalId, Integer anchoMm, boolean escpos) {
        com.franco.dev.domain.rrhh.LiquidacionFinal lf = liquidacionFinalService.findById(liquidacionFinalId)
                .orElseThrow(() -> new GraphQLException("Liquidacion final no encontrada"));
        com.franco.dev.domain.personas.Funcionario f = lf.getFuncionario();

        List<FiniquitoRow> filas = new ArrayList<>();
        for (com.franco.dev.domain.rrhh.LiquidacionFinalItem it : liquidacionFinalService.findItems(liquidacionFinalId)) {
            String concepto = it.getDescripcion() != null ? it.getDescripcion()
                    : (it.getConcepto() != null ? it.getConcepto().name() : "");
            boolean esDescuento = it.getTipo() == com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo.DESCUENTO;
            // Guaraníes sin decimales; los descuentos entre paréntesis (restan).
            String montoStr = formatoGs.format(it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO);
            if (esDescuento) montoStr = "(" + montoStr + ")";
            filas.add(new FiniquitoRow(concepto, montoStr));
        }
        if (filas.isEmpty()) filas.add(new FiniquitoRow("SIN ITEMS", "0"));

        BigDecimal totalLiq = lf.getTotalLiquidado() != null ? lf.getTotalLiquidado() : BigDecimal.ZERO;

        // Formato ticket: plantilla genérica angosta.
        if (anchoMm != null) {
            String clausula = "Recibí conforme, en concepto de liquidación final de haberes,";
            return reciboRrhh("LIQUIDACION FINAL", f, filas, totalLiq, clausula, anchoMm, escpos);
        }

        java.time.LocalDate ingreso = f != null && f.getFechaIngreso() != null ? f.getFechaIngreso().toLocalDate() : null;
        java.time.LocalDate egreso = lf.getFechaEgreso();
        BigDecimal sueldo = f != null && f.getSueldo() != null ? new BigDecimal(f.getSueldo().toString()) : BigDecimal.ZERO;
        BigDecimal jornal = sueldo.divide(new BigDecimal("30"), 0, java.math.RoundingMode.HALF_UP);
        String documento = f != null && f.getPersona() != null && f.getPersona().getDocumento() != null
                ? f.getPersona().getDocumento() : "";

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", razonSocialEmpresa());
        params.put("trabajador", nombreFuncionario(f));
        params.put("documento", documento);
        params.put("motivo", lf.getMotivoEgreso() != null ? lf.getMotivoEgreso().name() : "");
        params.put("entrada", ingreso != null ? ingreso.toString() : "");
        params.put("salida", egreso != null ? egreso.toString() : "");
        params.put("antiguedad", antiguedadTexto(ingreso, egreso));
        params.put("salario", formatoGs.format(sueldo));
        params.put("jornalDiario", formatoGs.format(jornal));
        params.put("fecha", LocalDate.now().toString());
        params.put("total", formatoGs.format(totalLiq));
        params.put("totalEnLetras", enLetrasGs(totalLiq));

        return generar("reports/recibo-finiquito.jrxml", params, filas);
    }


    /** Row para recibo-finiquito.jrxml (fields concepto, monto). */
    /** Razón social de la empresa (ConfiguracionGeneral), no el nombre de la sucursal. */
    private String razonSocialEmpresa() {
        java.util.List<com.franco.dev.domain.empresarial.ConfiguracionGeneral> all = configuracionGeneralService.findAll2();
        com.franco.dev.domain.empresarial.ConfiguracionGeneral cg = all != null && !all.isEmpty() ? all.get(0) : null;
        if (cg != null) {
            if (cg.getRazonSocial() != null) return cg.getRazonSocial();
            if (cg.getNombreEmpresa() != null) return cg.getNombreEmpresa();
        }
        return "";
    }

    /** "X año(s) Y mes(es) Z día(s)" entre ingreso y egreso. */
    private String antiguedadTexto(java.time.LocalDate ingreso, java.time.LocalDate egreso) {
        if (ingreso == null || egreso == null || egreso.isBefore(ingreso)) return "";
        java.time.Period p = java.time.Period.between(ingreso, egreso);
        return p.getYears() + " año(s) " + p.getMonths() + " mes(es) " + p.getDays() + " día(s)";
    }

    /** Total en palabras (guaraníes, entero). */
    private String enLetrasGs(BigDecimal monto) {
        BigDecimal v = monto != null ? monto : BigDecimal.ZERO;
        boolean neg = v.signum() < 0;
        String entero = v.abs().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        String palabras = numeroALetrasService.converter(entero, true);
        String txt = palabras != null ? palabras.trim() + " GUARANIES" : "";
        return neg ? "MENOS " + txt : txt;
    }

    public static class FiniquitoRow {
        private final String concepto;
        private final String monto;
        public FiniquitoRow(String concepto, String monto) { this.concepto = concepto; this.monto = monto; }
        public String getConcepto() { return concepto; }
        public String getMonto() { return monto; }
    }

    /** Reporte de vales pendientes (SOLICITADO + CONFIRMADO). */
    @Transactional(readOnly = true)
    public String reporteValesPendientesBase64() {
        List<com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto> filas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (com.franco.dev.domain.rrhh.enums.ValeEstado est : new com.franco.dev.domain.rrhh.enums.ValeEstado[]{
                com.franco.dev.domain.rrhh.enums.ValeEstado.SOLICITADO, com.franco.dev.domain.rrhh.enums.ValeEstado.CONFIRMADO}) {
            for (com.franco.dev.domain.rrhh.Vale v : valeRepository.findByEstadoOrderByFechaDesc(est)) {
                filas.add(new com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto(
                        nombreFuncionario(v.getFuncionario()), formatear(v.getMonto()),
                        v.getEstado() != null ? v.getEstado().name() : "",
                        v.getFecha() != null ? v.getFecha().toString() : ""));
                if (v.getMonto() != null) total = total.add(v.getMonto());
            }
        }
        Map<String, Object> params = paramsGenericos("VALES PENDIENTES", "Vales solicitados / confirmados sin descontar",
                "Funcionario", "Monto", "Estado", "Fecha", "Total:", formatear(total));
        return generarGenerico(params, filas);
    }

    /** Reporte de préstamos activos con saldo pendiente. */
    @Transactional(readOnly = true)
    public String reportePrestamosActivosBase64() {
        List<com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto> filas = new ArrayList<>();
        BigDecimal totalSaldo = BigDecimal.ZERO;
        for (com.franco.dev.domain.rrhh.Prestamo p : prestamoRepository.findByEstadoOrderByFechaInicioDesc(
                com.franco.dev.domain.rrhh.enums.PrestamoEstado.ACTIVO)) {
            BigDecimal tot = p.getMontoTotal() != null ? p.getMontoTotal() : BigDecimal.ZERO;
            BigDecimal pag = p.getMontoPagado() != null ? p.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal saldo = tot.subtract(pag).max(BigDecimal.ZERO);
            filas.add(new com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto(
                    nombreFuncionario(p.getFuncionario()), formatear(tot), formatear(saldo),
                    p.getEstado() != null ? p.getEstado().name() : ""));
            totalSaldo = totalSaldo.add(saldo);
        }
        Map<String, Object> params = paramsGenericos("PRESTAMOS ACTIVOS", "Prestamos a funcionarios con saldo pendiente",
                "Funcionario", "Monto total", "Saldo", "Estado", "Total saldo:", formatear(totalSaldo));
        return generarGenerico(params, filas);
    }

    /** Reporte de aguinaldos del año. */
    @Transactional(readOnly = true)
    public String reporteAguinaldoAnualBase64(Integer anio) {
        if (anio == null) throw new GraphQLException("Anio requerido");
        List<com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto> filas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (com.franco.dev.domain.rrhh.Aguinaldo a : aguinaldoRepository.findByAnioOrderByIdAsc(anio)) {
            filas.add(new com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto(
                    nombreFuncionario(a.getFuncionario()),
                    String.valueOf(a.getMesesTrabajados() != null ? a.getMesesTrabajados() : 0),
                    formatear(a.getMontoCalculado()),
                    a.getEstado() != null ? a.getEstado().name() : ""));
            if (a.getMontoCalculado() != null) total = total.add(a.getMontoCalculado());
        }
        Map<String, Object> params = paramsGenericos("AGUINALDO " + anio, "Aguinaldo del anio " + anio,
                "Funcionario", "Meses", "Monto", "Estado", "Total:", formatear(total));
        return generarGenerico(params, filas);
    }

    // ===== Recibos firmables por registro (recibo-rrhh.jrxml) =====

    /** Recibo de vale / adelanto. anchoMm null = PDF A4; 58/80 = ticket. */
    @Transactional(readOnly = true)
    public String reciboValeBase64(Long id, Integer anchoMm, boolean escpos) {
        com.franco.dev.domain.rrhh.Vale v = valeRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("Vale no encontrado"));
        boolean adelanto = Boolean.TRUE.equals(v.getEsAdelanto());
        String concepto = (v.getMotivo() != null && v.getMotivo().getNombre() != null ? v.getMotivo().getNombre()
                : (adelanto ? "ADELANTO DE SALARIO" : "VALE"))
                + (v.getFecha() != null ? " (" + v.getFecha() + ")" : "");
        List<FiniquitoRow> filas = new ArrayList<>();
        filas.add(new FiniquitoRow(concepto, formatoGs.format(nz(v.getMonto()))));
        String clausula = "Recibí conforme, en concepto de " + (adelanto ? "adelanto de salario" : "vale") + ",";
        return reciboRrhh("RECIBO DE " + (adelanto ? "ADELANTO" : "VALE"), v.getFuncionario(), filas, nz(v.getMonto()), clausula, anchoMm, escpos);
    }

    /** Recibo / notificación de penalización. */
    @Transactional(readOnly = true)
    public String reciboPenalizacionBase64(Long id, Integer anchoMm, boolean escpos) {
        com.franco.dev.domain.rrhh.Penalizacion p = penalizacionRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("Penalizacion no encontrada"));
        String tipo = p.getTipo() != null ? p.getTipo().name() : "PENALIZACION";
        String concepto = tipo + (p.getDescripcion() != null && !p.getDescripcion().isEmpty() ? " - " + p.getDescripcion() : "")
                + (p.getFecha() != null ? " (" + p.getFecha() + ")" : "");
        List<FiniquitoRow> filas = new ArrayList<>();
        filas.add(new FiniquitoRow(concepto, formatoGs.format(nz(p.getMonto()))));
        String clausula = "Tomo conocimiento de la penalización aplicada y su descuento correspondiente,";
        return reciboRrhh("RECIBO DE PENALIZACION", p.getFuncionario(), filas, nz(p.getMonto()), clausula, anchoMm, escpos);
    }

    /** Recibo de aguinaldo. */
    @Transactional(readOnly = true)
    public String reciboAguinaldoBase64(Long id, Integer anchoMm, boolean escpos) {
        com.franco.dev.domain.rrhh.Aguinaldo a = aguinaldoRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("Aguinaldo no encontrado"));
        String concepto = "AGUINALDO " + (a.getAnio() != null ? a.getAnio() : "")
                + (a.getMesesTrabajados() != null ? " (" + a.getMesesTrabajados() + " meses)" : "");
        List<FiniquitoRow> filas = new ArrayList<>();
        filas.add(new FiniquitoRow(concepto, formatoGs.format(nz(a.getMontoCalculado()))));
        String clausula = "Recibí conforme, en concepto de aguinaldo,";
        return reciboRrhh("RECIBO DE AGUINALDO", a.getFuncionario(), filas, nz(a.getMontoCalculado()), clausula, anchoMm, escpos);
    }

    /** Recibo de entrega de préstamo (desembolso al funcionario). */
    @Transactional(readOnly = true)
    public String reciboPrestamoBase64(Long id, Integer anchoMm, boolean escpos) {
        com.franco.dev.domain.rrhh.Prestamo p = prestamoRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("Prestamo no encontrado"));
        String concepto = "PRESTAMO"
                + (p.getDescripcion() != null && !p.getDescripcion().isEmpty() ? " - " + p.getDescripcion() : "")
                + (p.getCantidadCuotas() != null ? " (" + p.getCantidadCuotas() + " cuotas)" : "");
        List<FiniquitoRow> filas = new ArrayList<>();
        filas.add(new FiniquitoRow(concepto, formatoGs.format(nz(p.getMontoTotal()))));
        String clausula = "Recibí conforme el importe entregado en concepto de préstamo, a descontar en las cuotas pactadas,";
        return reciboRrhh("RECIBO DE PRESTAMO", p.getFuncionario(), filas, nz(p.getMontoTotal()), clausula, anchoMm, escpos);
    }

    /** Recibo de bono. */
    @Transactional(readOnly = true)
    public String reciboBonoBase64(Long id, Integer anchoMm, boolean escpos) {
        com.franco.dev.domain.rrhh.Bono b = bonoRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("Bono no encontrado"));
        String tipo = b.getTipo() != null ? b.getTipo().name() : "BONO";
        String concepto = tipo + (b.getMotivo() != null && !b.getMotivo().isEmpty() ? " - " + b.getMotivo() : "")
                + (b.getFecha() != null ? " (" + b.getFecha() + ")" : "");
        List<FiniquitoRow> filas = new ArrayList<>();
        filas.add(new FiniquitoRow(concepto, formatoGs.format(nz(b.getMonto()))));
        String clausula = "Recibí conforme, en concepto de bono,";
        return reciboRrhh("RECIBO DE BONO", b.getFuncionario(), filas, nz(b.getMonto()), clausula, anchoMm, escpos);
    }

    /**
     * Builder común de los recibos firmables.
     * - escpos=true → payload ESC/POS base64 (ticket térmico, para print-local del cliente).
     * - escpos=false: anchoMm null → PDF A4; 58/80 → PDF ticket angosto (preview en visor).
     */
    private String reciboRrhh(String titulo, Funcionario f, List<FiniquitoRow> filas, BigDecimal total,
                              String clausula, Integer anchoMm, boolean escpos) {
        if (filas.isEmpty()) filas.add(new FiniquitoRow("-", "0"));
        if (escpos) {
            List<com.franco.dev.utilitarios.print.ReciboTicketEscPos.Row> rows = new ArrayList<>();
            for (FiniquitoRow r : filas) {
                rows.add(new com.franco.dev.utilitarios.print.ReciboTicketEscPos.Row(r.getConcepto(), r.getMonto()));
            }
            return com.franco.dev.utilitarios.print.ReciboTicketEscPos.build(
                    razonSocialEmpresa(), titulo, nombreFuncionario(f), documentoOf(f), LocalDate.now().toString(),
                    rows, formatoGs.format(nz(total)), enLetrasGs(total), clausula, anchoMm);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("empresa", razonSocialEmpresa());
        params.put("titulo", titulo);
        params.put("funcionario", nombreFuncionario(f));
        params.put("documento", documentoOf(f));
        params.put("fecha", LocalDate.now().toString());
        params.put("clausula", clausula);
        params.put("total", formatoGs.format(nz(total)));
        params.put("totalEnLetras", enLetrasGs(total));
        return generar(plantillaRecibo(anchoMm), params, filas);
    }

    /** Elige la plantilla según el formato: A4 (null) o ticket 58/80mm. */
    private String plantillaRecibo(Integer anchoMm) {
        if (anchoMm == null) return "reports/recibo-rrhh.jrxml";
        return anchoMm >= 80 ? "reports/recibo-ticket-80.jrxml" : "reports/recibo-ticket-58.jrxml";
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private String documentoOf(Funcionario f) {
        return f != null && f.getPersona() != null && f.getPersona().getDocumento() != null
                ? f.getPersona().getDocumento() : "";
    }

    private Map<String, Object> paramsGenericos(String titulo, String subtitulo, String h1, String h2, String h3, String h4,
                                                String totalLabel, String totalValue) {
        Map<String, Object> params = new HashMap<>();
        params.put("empresa", "");
        params.put("titulo", titulo);
        params.put("subtitulo", subtitulo);
        params.put("fecha", LocalDate.now().toString());
        params.put("h1", h1); params.put("h2", h2); params.put("h3", h3); params.put("h4", h4);
        params.put("totalLabel", totalLabel); params.put("totalValue", totalValue);
        return params;
    }

    private String generarGenerico(Map<String, Object> params, List<com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto> filas) {
        if (filas.isEmpty()) filas.add(new com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto("SIN DATOS", "", "", ""));
        return generar("reports/reporte-rrhh-generico.jrxml", params, filas);
    }

    private String generar(String classpathReport, Map<String, Object> params, List<?> filas) {
        try {
            File file = ResourceUtils.getFile("classpath:" + classpathReport);
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(filas);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return java.util.Base64.getEncoder().encodeToString(pdfBytes);
        } catch (Exception e) {
            throw new GraphQLException("Error generando el reporte: " + e.getMessage());
        }
    }

    private void validarPeriodo(String periodo) {
        if (periodo == null || !periodo.matches("\\d{4}-\\d{2}")) {
            throw new GraphQLException("Periodo invalido, se espera 'YYYY-MM'");
        }
    }

    private String formatear(BigDecimal valor) {
        return formato.format(valor != null ? valor : BigDecimal.ZERO);
    }

    private String nombreFuncionario(Funcionario f) {
        if (f != null && f.getPersona() != null && f.getPersona().getNombre() != null) {
            return f.getPersona().getNombre();
        }
        return f != null && f.getId() != null ? "#" + f.getId() : "";
    }

    private String empresa(String periodo) {
        // primera liquidación del período con sucursal, si hay
        for (LiquidacionSueldo l : liquidacionSueldoRepository.findByPeriodoOrderByIdAsc(periodo)) {
            if (l.getFuncionario() != null && l.getFuncionario().getSucursal() != null
                    && l.getFuncionario().getSucursal().getNombre() != null) {
                return l.getFuncionario().getSucursal().getNombre();
            }
        }
        return "";
    }
}
