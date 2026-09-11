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

    /**
     * Periodo sin liquidaciones: whenNoDataType=AllSectionsNoDetail tiene que imprimir el
     * reporte igual (encabezado + totales en cero) y no reventar con el datasource vacio.
     */
    @Test
    void sinLiquidacionesImprimeIgual() throws Exception {
        File f = ResourceUtils.getFile("classpath:reports/nomina-mes.jrxml");
        JasperReport jr = JasperCompileManager.compileReport(f.getAbsolutePath());

        Map<String, Object> p = new HashMap<>();
        p.put("empresa", "");
        p.put("periodo", "2026-09");
        p.put("fecha", "2026-09-10");
        p.put("totalNeto", "0");
        p.put("totalBanco", "0");
        p.put("totalEfectivo", "0");
        p.put("cantidadBanco", 0);
        p.put("cantidadEfectivo", 0);
        p.put("ciudad", "SIN CIUDAD ASIGNADA");

        JasperPrint print = JasperFillManager.fillReport(jr, p,
                new JRBeanCollectionDataSource(java.util.Collections.emptyList()));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        org.junit.jupiter.api.Assertions.assertTrue(pdf != null && pdf.length > 0);
        org.junit.jupiter.api.Assertions.assertEquals(1, print.getPages().size(),
                "el reporte vacio tiene que seguir imprimiendo una pagina");

        String texto = textoDe(print);
        org.junit.jupiter.api.Assertions.assertTrue(texto.contains("SIN LIQUIDACIONES"),
                "el reporte vacio tiene que decir que no hay liquidaciones: " + texto);
        // Sin filas no puede aparecer ningun grupo ni subtotal: es lo que salia mal antes,
        // con "SUBTOTAL EFECTIVO (null): null" bajo un grupo COBRAN EN EFECTIVO fantasma.
        org.junit.jupiter.api.Assertions.assertFalse(texto.contains("SUBTOTAL"),
                "el reporte vacio no puede imprimir subtotales de grupo: " + texto);
        org.junit.jupiter.api.Assertions.assertFalse(texto.contains("COBRAN"),
                "el reporte vacio no puede imprimir encabezados de grupo: " + texto);
        org.junit.jupiter.api.Assertions.assertFalse(texto.contains("null"),
                "el reporte vacio no puede imprimir 'null': " + texto);
    }

    /** Texto plano de todas las páginas, para poder afirmar sobre el contenido y no solo el tamaño. */
    private String textoDe(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        for (Object pagina : print.getPages()) {
            for (Object el : ((net.sf.jasperreports.engine.JRPrintPage) pagina).getElements()) {
                if (el instanceof net.sf.jasperreports.engine.JRPrintText) {
                    sb.append(((net.sf.jasperreports.engine.JRPrintText) el).getFullText()).append(" | ");
                }
            }
        }
        return sb.toString();
    }
}
