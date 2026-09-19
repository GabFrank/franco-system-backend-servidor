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
 * Valida que recibo-finiquito.jrxml compila, rellena y exporta a PDF con datos
 * dummy. El jrxml se compila en runtime (no lo cubre el build), así que este test
 * caza errores de plantilla antes de que revienten al generar el recibo real.
 */
public class ReciboFiniquitoJrxmlTest {

    public static class Row {
        private final String concepto;
        private final String monto;
        public Row(String concepto, String monto) { this.concepto = concepto; this.monto = monto; }
        public String getConcepto() { return concepto; }
        public String getMonto() { return monto; }
    }

    @Test
    void compilaRellenaYExporta() throws Exception {
        File f = ResourceUtils.getFile("classpath:reports/recibo-finiquito.jrxml");
        JasperReport jr = JasperCompileManager.compileReport(f.getAbsolutePath());

        Map<String, Object> p = new HashMap<>();
        for (String k : new String[]{"empresa", "trabajador", "documento", "motivo", "entrada",
                "salida", "antiguedad", "salario", "jornalDiario", "fecha", "total", "totalEnLetras"}) {
            p.put(k, "DATO DUMMY");
        }
        List<Row> rows = Arrays.asList(
                new Row("SALARIO DEL MES (22 DIAS TRABAJADOS)", "2.566.667"),
                new Row("VACACIONES NO GOZADAS (7 DIAS)", "1.009.653"),
                new Row("DESCUENTO IPS", "(389.437)"));
        JasperPrint print = JasperFillManager.fillReport(jr, p, new JRBeanCollectionDataSource(rows));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        org.junit.jupiter.api.Assertions.assertTrue(pdf != null && pdf.length > 0);
    }

    /** El numero de la liquidacion final va en el titulo, y la observacion en su propia linea. */
    @Test
    void imprimeNumeroYObservacion() throws Exception {
        String conObs = ReciboRrhhJrxmlTest.textosDelPrint(llenar("7", "RENUNCIA PRESENTADA EL 01/09, SE PAGA EN DOS CUOTAS"));
        for (String esperado : new String[]{"LIQUIDACION FINAL DE HABERES Nro. 7",
                "Obs.: RENUNCIA PRESENTADA EL 01/09, SE PAGA EN DOS CUOTAS"}) {
            org.junit.jupiter.api.Assertions.assertTrue(conObs.contains(esperado),
                    "falta [" + esperado + "]. Textos: " + conObs);
        }
        String sinObs = ReciboRrhhJrxmlTest.textosDelPrint(llenar(null, null));
        org.junit.jupiter.api.Assertions.assertTrue(sinObs.contains("LIQUIDACION FINAL DE HABERES"), sinObs);
        org.junit.jupiter.api.Assertions.assertFalse(sinObs.contains("Nro.") || sinObs.contains("Obs."),
                "sin numero ni observacion no tienen que aparecer: " + sinObs);
    }

    private JasperPrint llenar(String numero, String observacion) throws Exception {
        JasperReport jr = JasperCompileManager.compileReport(
                ResourceUtils.getFile("classpath:reports/recibo-finiquito.jrxml").getAbsolutePath());
        Map<String, Object> p = new HashMap<>();
        for (String k : new String[]{"empresa", "trabajador", "documento", "motivo", "entrada",
                "salida", "antiguedad", "salario", "jornalDiario", "fecha", "total", "totalEnLetras"}) {
            p.put(k, "DATO DUMMY");
        }
        p.put("numero", numero);
        p.put("observacion", observacion);
        return JasperFillManager.fillReport(jr, p, new JRBeanCollectionDataSource(
                Arrays.asList(new Row("SALARIO DEL MES (22 DIAS TRABAJADOS)", "2.566.667"))));
    }
}
