package com.franco.dev.reports;

import com.franco.dev.service.rrhh.dto.NominaMesItemDto;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Valida que nomina-mes.jrxml compila, rellena y exporta a PDF, incluyendo el
 * agrupamiento por forma de cobro (BANCO / EFECTIVO) con sus subtotales.
 */
public class NominaMesJrxmlTest {

    @Test
    void compilaRellenaYExporta() throws Exception {
        File f = ResourceUtils.getFile("classpath:reports/nomina-mes.jrxml");
        JasperReport jr = JasperCompileManager.compileReport(f.getAbsolutePath());

        Map<String, Object> p = new HashMap<>();
        p.put("empresa", "SUCURSAL DE PRUEBA");
        p.put("periodo", "2026-09");
        p.put("fecha", "2026-09-10");
        p.put("totalNeto", "9.000.000");
        p.put("totalBanco", "6.000.000");
        p.put("totalEfectivo", "3.000.000");
        p.put("cantidadBanco", 2);
        p.put("cantidadEfectivo", 1);
        p.put("ciudad", "SALTO DEL GUAIRA");

        // Mismo orden que arma el servicio: primero BANCO, despues EFECTIVO.
        List<NominaMesItemDto> filas = Arrays.asList(
                new NominaMesItemDto("FUNCIONARIO UNO", "3.500.000", "500.000", "3.000.000",
                        "BANCO", new BigDecimal("3000000")),
                new NominaMesItemDto("FUNCIONARIO DOS", "3.500.000", "500.000", "3.000.000",
                        "BANCO", new BigDecimal("3000000")),
                new NominaMesItemDto("FUNCIONARIO TRES", "3.500.000", "500.000", "3.000.000",
                        "EFECTIVO", new BigDecimal("3000000")));

        JasperPrint print = JasperFillManager.fillReport(jr, p, new JRBeanCollectionDataSource(filas));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        org.junit.jupiter.api.Assertions.assertTrue(pdf != null && pdf.length > 0);
    }
}
