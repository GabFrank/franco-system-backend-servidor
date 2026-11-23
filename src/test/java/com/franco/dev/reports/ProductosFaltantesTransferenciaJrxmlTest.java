package com.franco.dev.reports;

import com.franco.dev.service.impresion.ProductoFaltanteReporteItem;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * productos-faltantes-transferencia.jrxml compila, llena y exporta. Se miran los textos llenados y no el peso
 * del PDF: un campo con alto insuficiente sale vacio sin error.
 */
class ProductosFaltantesTransferenciaJrxmlTest {

    private static JasperPrint llenar(List<ProductoFaltanteReporteItem> filas, String aviso) throws Exception {
        File f = ResourceUtils.getFile("classpath:reports/productos-faltantes-transferencia.jrxml");
        JasperReport jr = JasperCompileManager.compileReport(f.getAbsolutePath());
        Map<String, Object> p = new HashMap<>();
        p.put("logo", ResourceUtils.getFile("classpath:no-image.png").getAbsolutePath());
        p.put("periodo", "01/07/2026 al 10/10/2026");
        p.put("motivo", "FALTA PRODUCTO");
        p.put("origen", "DEPOSITO AQUARIO SDG");
        p.put("destino", "TODAS");
        p.put("filtroProducto", "TODOS");
        p.put("fechaReporte", "18/11/2026 09:00");
        p.put("usuario", "MAURO ROLANDO RIVAS FERNANDEZ");
        p.put("aviso", aviso);
        p.put(net.sf.jasperreports.engine.JRParameter.REPORT_LOCALE, new java.util.Locale("es", "PY")); // igual que ImpresionService
        return JasperFillManager.fillReport(jr, p, new JRBeanCollectionDataSource(filas));
    }

    private static ProductoFaltanteReporteItem fila(long id) {
        ProductoFaltanteReporteItem r = new ProductoFaltanteReporteItem();
        r.setTransferenciaId(id);
        r.setFecha("23/09/2026 10:09");
        r.setOrigen("DEPOSITO AQUARIO SDG");
        r.setDestino("SUC. NUEVA ESPERANZA SAN ANTONIO");
        r.setProducto("MASTER GOLD WHISKY MALTA Y ROBLE 900 ML");
        r.setCantidadPedida(6.0);
        r.setCantidadPreparada(1.5);
        r.setEtapa("Preparación (origen)");
        r.setResponsable("MAURO ARIEL RUIZ GONZÁLEZ");
        r.setEstado("EN TRANSITO");
        return r;
    }

    private static String textos(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        for (JRPrintPage page : print.getPages())
            for (JRPrintElement e : page.getElements())
                if (e instanceof JRPrintText) sb.append(((JRPrintText) e).getFullText()).append('\n');
        return sb.toString();
    }

    @Test
    void llenaTodasLasColumnasYLaCabecera() throws Exception {
        List<ProductoFaltanteReporteItem> filas = new ArrayList<>();
        for (long i = 0; i < 60; i++) filas.add(fila(58100 + i));
        JasperPrint print = llenar(filas, "Resultado truncado: se muestran los primeros 5000 de 7499 ítems.");
        assertTrue(JasperExportManager.exportReportToPdf(print).length > 0);
        assertTrue(print.getPages().size() > 1, "60 filas no entran en una hoja apaisada");

        String t = textos(print);
        for (String esperado : new String[]{"Productos faltantes en transferencias", "01/07/2026 al 10/10/2026",
                "FALTA PRODUCTO", "DEPOSITO AQUARIO SDG", "SUC. NUEVA ESPERANZA SAN ANTONIO",
                "MASTER GOLD WHISKY MALTA Y ROBLE 900 ML", "58159", "23/09/2026 10:09", "Preparación (origen)",
                "MAURO ARIEL RUIZ GONZÁLEZ", "EN TRANSITO", "1,5", "Rechazado en",
                "18/11/2026 09:00 por MAURO ROLANDO RIVAS FERNANDEZ", "5000 de 7499", "TOTAL: 60 ítems"}) {
            assertTrue(t.contains(esperado), "falta en el PDF: " + esperado);
        }
    }

    @Test
    void sinAvisoNoImprimeLaLineaRoja() throws Exception {
        List<ProductoFaltanteReporteItem> filas = new ArrayList<>();
        filas.add(fila(1));
        String t = textos(llenar(filas, ""));
        assertFalse(t.contains("truncado"));
        assertTrue(t.contains("TOTAL: 1 ítems"));
    }

    /**
     * Jasper mide con la fuente del sistema y el PDF dibuja en Helvetica, mas ancha: un nombre que Jasper cree que
     * entra en una linea sale en dos en el PDF. Sin alto para dos lineas se perdia «CENTRO». Se lee el PDF exportado.
     */
    @Test
    void enElPdfNoSePierdeLaUltimaPalabraDeUnNombreLargo() throws Exception {
        ProductoFaltanteReporteItem r = fila(58147);
        r.setOrigen("SUC. NUEVA ESPERANZA SAN ANTONIO");
        r.setDestino("SUC. NUEVA ESPERANZA CENTRO");
        List<ProductoFaltanteReporteItem> filas = new ArrayList<>();
        filas.add(r);
        byte[] pdf = JasperExportManager.exportReportToPdf(llenar(filas, ""));
        String texto = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(
                new com.itextpdf.text.pdf.PdfReader(pdf), 1);
        for (String esperado : new String[]{"CENTRO", "ANTONIO", "GONZÁLEZ", "TRANSITO"}) {
            assertTrue(texto.contains(esperado), "el PDF perdio: " + esperado);
        }
    }
}
