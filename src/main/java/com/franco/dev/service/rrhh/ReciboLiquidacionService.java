package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.service.rrhh.dto.ReciboLiquidacionItemDto;
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
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera el recibo de sueldo (PDF base64) de una liquidación vía JasperReports.
 * Patrón: cargar .jrxml → compilar → fill con params + datasource → export PDF → base64.
 */
@Service
public class ReciboLiquidacionService {

    private final LiquidacionSueldoService liquidacionSueldoService;
    private final DecimalFormat formato = new DecimalFormat("#,##0.##");

    public ReciboLiquidacionService(LiquidacionSueldoService liquidacionSueldoService) {
        this.liquidacionSueldoService = liquidacionSueldoService;
    }

    @Transactional(readOnly = true)
    public String generarBase64(Long liquidacionId) {
        LiquidacionSueldo liq = liquidacionSueldoService.findById(liquidacionId)
                .orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));

        List<ReciboLiquidacionItemDto> filas = new ArrayList<>();
        for (LiquidacionItem it : liquidacionSueldoService.findItems(liquidacionId)) {
            filas.add(new ReciboLiquidacionItemDto(
                    it.getDescripcion(),
                    it.getTipo() != null ? it.getTipo().name() : "",
                    formatear(it.getMonto())));
        }
        // JasperReports omite el detail si el datasource está vacío
        if (filas.isEmpty()) {
            filas.add(new ReciboLiquidacionItemDto("SIN ITEMS", "", "0"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", nombreSucursal(liq));
        params.put("funcionario", nombreFuncionario(liq));
        params.put("cargo", liq.getFuncionario() != null && liq.getFuncionario().getCargo() != null
                ? liq.getFuncionario().getCargo().getNombre() : null);
        params.put("documento", documentoFuncionario(liq));
        params.put("periodo", liq.getPeriodo());
        params.put("fecha", LocalDate.now().toString());
        params.put("totalHaberes", formatear(liq.getTotalHaberes()));
        params.put("totalDescuentos", formatear(liq.getTotalDescuentos()));
        params.put("totalNeto", formatear(liq.getTotalNeto()));

        try {
            File file = ResourceUtils.getFile("classpath:reports/recibo-liquidacion.jrxml");
            JasperReport jasperReport = JasperCompileManager.compileReport(file.getAbsolutePath());
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(filas);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return java.util.Base64.getEncoder().encodeToString(pdfBytes);
        } catch (Exception e) {
            throw new GraphQLException("Error generando el recibo: " + e.getMessage());
        }
    }

    private String formatear(BigDecimal valor) {
        return formato.format(valor != null ? valor : BigDecimal.ZERO);
    }

    private String nombreFuncionario(LiquidacionSueldo liq) {
        if (liq.getFuncionario() != null && liq.getFuncionario().getPersona() != null
                && liq.getFuncionario().getPersona().getNombre() != null) {
            return liq.getFuncionario().getPersona().getNombre();
        }
        return "";
    }

    private String documentoFuncionario(LiquidacionSueldo liq) {
        if (liq.getFuncionario() != null && liq.getFuncionario().getPersona() != null) {
            return liq.getFuncionario().getPersona().getDocumento();
        }
        return "";
    }

    private String nombreSucursal(LiquidacionSueldo liq) {
        if (liq.getFuncionario() != null && liq.getFuncionario().getSucursal() != null) {
            return liq.getFuncionario().getSucursal().getNombre();
        }
        return "";
    }
}
