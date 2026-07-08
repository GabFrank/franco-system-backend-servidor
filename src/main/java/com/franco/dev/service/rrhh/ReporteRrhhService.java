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
    private final DecimalFormat formato = new DecimalFormat("#,##0.##");

    public ReporteRrhhService(LiquidacionSueldoRepository liquidacionSueldoRepository,
                              ConfiguracionRrhhService configuracionRrhhService) {
        this.liquidacionSueldoRepository = liquidacionSueldoRepository;
        this.configuracionRrhhService = configuracionRrhhService;
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
