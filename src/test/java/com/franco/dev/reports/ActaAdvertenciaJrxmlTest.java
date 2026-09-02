package com.franco.dev.reports;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JREmptyDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida que acta-advertencia.jrxml compila, rellena y exporta a PDF.
 *
 * <p>Los .jrxml se compilan en runtime, asi que un error de plantilla no lo caza el build
 * ni CI: revienta al generar el acta. Este test es la unica red.</p>
 */
public class ActaAdvertenciaJrxmlTest {

    private static final String[] PARAMS = {
            "empresa", "ruc", "direccionEmpresa", "telefonoEmpresa", "funcionario",
            "documento", "cargo", "numeroAdvertencia", "motivo", "fechaHecho", "fecha", "ciudad"
    };

    private JasperPrint llenar(Map<String, Object> p) throws Exception {
        File f = ResourceUtils.getFile("classpath:reports/acta-advertencia.jrxml");
        JasperReport jr = JasperCompileManager.compileReport(f.getAbsolutePath());
        return JasperFillManager.fillReport(jr, p, new JREmptyDataSource());
    }

    private Map<String, Object> paramsDummy() {
        Map<String, Object> p = new HashMap<>();
        for (String k : PARAMS) p.put(k, "DATO DUMMY");
        return p;
    }

    @Test
    void compilaRellenaYExporta() throws Exception {
        Map<String, Object> p = paramsDummy();
        p.put("motivo", "SE RETIRO DEL PUESTO SIN AVISO DURANTE EL TURNO DE LA TARDE, "
                + "DEJANDO LA CAJA SIN COBERTURA POR APROXIMADAMENTE 40 MINUTOS.");
        p.put("numeroAdvertencia", "2");
        byte[] pdf = JasperExportManager.exportReportToPdf(llenar(p));
        assertTrue(pdf != null && pdf.length > 0, "el acta salio vacia");
    }

    /** El acta es un documento de una hoja: si el motivo crece no debe partirse. */
    @Test
    void unMotivoLargoSigueEntrandoEnUnaHoja() throws Exception {
        Map<String, Object> p = paramsDummy();
        StringBuilder motivo = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            motivo.append("Incumplimiento reiterado de las instrucciones impartidas por su superior directo. ");
        }
        p.put("motivo", motivo.toString());
        assertEquals(1, llenar(p).getPages().size(), "el acta tiene que entrar en una hoja");
    }

    /** Sin numero ni datos opcionales de empresa, el acta se genera igual. */
    @Test
    void soportaParametrosVacios() throws Exception {
        Map<String, Object> p = new HashMap<>();
        for (String k : PARAMS) p.put(k, "");
        byte[] pdf = JasperExportManager.exportReportToPdf(llenar(p));
        assertTrue(pdf != null && pdf.length > 0);
    }
}
