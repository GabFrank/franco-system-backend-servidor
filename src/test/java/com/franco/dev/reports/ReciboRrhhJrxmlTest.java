package com.franco.dev.reports;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Valida que recibo-rrhh.jrxml (recibo firmable genérico de vale/penalización/
 * aguinaldo/préstamo/bono) compila, rellena y exporta a PDF con datos dummy.
 */
public class ReciboRrhhJrxmlTest {

    public static class Row {
        private final String concepto;
        private final String monto;
        public Row(String concepto, String monto) { this.concepto = concepto; this.monto = monto; }
        public String getConcepto() { return concepto; }
        public String getMonto() { return monto; }
    }

    @Test
    void compilaRellenaYExporta() throws Exception {
        // Valida las 3 plantillas de recibo firmable: A4 + ticket 58/80mm (mismos params/fields).
        for (String tpl : new String[]{"reports/recibo-rrhh.jrxml",
                "reports/recibo-ticket-58.jrxml", "reports/recibo-ticket-80.jrxml"}) {
            File f = ResourceUtils.getFile("classpath:" + tpl);
            JasperReport jr = JasperCompileManager.compileReport(f.getAbsolutePath());

            Map<String, Object> p = new HashMap<>();
            for (String k : new String[]{"empresa", "titulo", "funcionario", "documento",
                    "fecha", "clausula", "total", "totalEnLetras"}) {
                p.put(k, "DATO DUMMY DE PRUEBA UN POCO LARGO PARA VER WRAP");
            }
            List<Row> rows = Arrays.asList(
                    new Row("ADELANTO DE SALARIO (2026-07-28)", "400.000"),
                    new Row("DESCUENTO IPS", "(389.437)"));
            JasperPrint print = JasperFillManager.fillReport(jr, p, new JRBeanCollectionDataSource(rows));
            byte[] pdf = JasperExportManager.exportReportToPdf(print);
            org.junit.jupiter.api.Assertions.assertTrue(pdf != null && pdf.length > 0, "Fallo plantilla " + tpl);

            // Un staticText cuyo alto no alcanza para su fuente se rellena VACIO, sin error:
            // el recibo sale sin encabezado de columnas y solo se nota mirando el PDF.
            // Ver que el texto sobrevivio al fill, no solo que el PDF pesa algo.
            String textos = textosDelPrint(print);
            for (String esperado : new String[]{"Concepto", "Monto"}) {
                org.junit.jupiter.api.Assertions.assertTrue(textos.contains(esperado),
                        "La plantilla " + tpl + " no imprime el encabezado '" + esperado
                                + "'. Suele ser el alto del staticText, que no entra para su fuente"
                                + " y Jasper lo recorta a vacio. Textos: " + textos);
            }
        }
    }

    /** Concatena el texto de todos los elementos del print, para aseverar sobre el resultado del fill. */
    private String textosDelPrint(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        for (net.sf.jasperreports.engine.JRPrintPage page : print.getPages()) {
            for (Object o : page.getElements()) {
                if (o instanceof net.sf.jasperreports.engine.JRPrintText) {
                    sb.append(((net.sf.jasperreports.engine.JRPrintText) o).getFullText()).append(" | ");
                }
            }
        }
        return sb.toString();
    }
}
