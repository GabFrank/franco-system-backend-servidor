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
    private final DecimalFormat formato = new DecimalFormat("#,##0.##");

    public ReporteRrhhService(LiquidacionSueldoRepository liquidacionSueldoRepository,
                              ConfiguracionRrhhService configuracionRrhhService,
                              LiquidacionFinalService liquidacionFinalService,
                              com.franco.dev.repository.rrhh.ValeRepository valeRepository,
                              com.franco.dev.repository.rrhh.PrestamoRepository prestamoRepository,
                              com.franco.dev.repository.rrhh.AguinaldoRepository aguinaldoRepository) {
        this.liquidacionSueldoRepository = liquidacionSueldoRepository;
        this.configuracionRrhhService = configuracionRrhhService;
        this.liquidacionFinalService = liquidacionFinalService;
        this.valeRepository = valeRepository;
        this.prestamoRepository = prestamoRepository;
        this.aguinaldoRepository = aguinaldoRepository;
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

    /** Recibo de finiquito (liquidación final) en PDF base64. */
    @Transactional(readOnly = true)
    public String finiquitoBase64(Long liquidacionFinalId) {
        com.franco.dev.domain.rrhh.LiquidacionFinal lf = liquidacionFinalService.findById(liquidacionFinalId)
                .orElseThrow(() -> new GraphQLException("Liquidacion final no encontrada"));
        List<com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto> filas = new ArrayList<>();
        for (com.franco.dev.domain.rrhh.LiquidacionFinalItem it : liquidacionFinalService.findItems(liquidacionFinalId)) {
            String concepto = it.getDescripcion() != null ? it.getDescripcion()
                    : (it.getConcepto() != null ? it.getConcepto().name() : "");
            // reutiliza el DTO generico: c1=concepto, c2=monto
            filas.add(new com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto(
                    concepto, formatear(it.getMonto()), null, null));
        }
        if (filas.isEmpty()) filas.add(new com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto("SIN ITEMS", "0", null, null));

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", lf.getFuncionario() != null && lf.getFuncionario().getSucursal() != null
                ? lf.getFuncionario().getSucursal().getNombre() : "");
        params.put("funcionario", nombreFuncionario(lf.getFuncionario()));
        params.put("motivo", lf.getMotivoEgreso() != null ? lf.getMotivoEgreso().name() : "");
        params.put("fechaEgreso", lf.getFechaEgreso() != null ? lf.getFechaEgreso().toString() : "");
        params.put("antiguedad", (lf.getAntiguedadAnios() != null ? lf.getAntiguedadAnios() : 0) + " años ("
                + (lf.getAntiguedadDias() != null ? lf.getAntiguedadDias() : 0) + " días)");
        params.put("salarioPromedio", formatear(lf.getSalarioPromedio()));
        params.put("fecha", LocalDate.now().toString());
        params.put("totalLiquidado", formatear(lf.getTotalLiquidado()));

        return generarFiniquito(params, filas);
    }

    private String generarFiniquito(Map<String, Object> params,
                                    List<com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto> filasGen) {
        // recibo-finiquito.jrxml usa fields concepto/monto
        List<FiniquitoRow> filas = new ArrayList<>();
        for (com.franco.dev.service.rrhh.dto.ReporteGenericoRowDto g : filasGen) {
            filas.add(new FiniquitoRow(g.getC1(), g.getC2()));
        }
        return generar("reports/recibo-finiquito.jrxml", params, filas);
    }

    /** Row para recibo-finiquito.jrxml (fields concepto, monto). */
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
