package com.franco.dev.reports;

import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.type.ModeEnum;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El .jrxml se compila en runtime: este test es el único gate. Los parámetros se arman a mano
 * porque los del KuDE de factura viven inline en FacturaLegalGraphQL, sin un service que llamar.
 */
class FacturaElectronicaKudeJrxmlTest {

    private static final String RUTA = "src/main/resources/reports/factura-electronica-kude.jrxml";
    private static final String CDC = "01800994825001001000000122026091512345678901";

    @Test
    void laPlantillaSoloUsaFuentesPermitidasYSiempreExplicitas() throws Exception {
        String jrxml = new String(Files.readAllBytes(Paths.get(RUTA)), StandardCharsets.UTF_8);

        int fuentes = 0;
        Matcher m = Pattern.compile("fontName=\"([^\"]*)\"").matcher(jrxml);
        while (m.find()) {
            fuentes++;
            assertTrue(Arrays.asList("SansSerif", "Verdana").contains(m.group(1)),
                    "fuente no permitida: " + m.group(1));
        }
        int etiquetas = jrxml.split("<font", -1).length - 1;
        assertEquals(etiquetas, fuentes, "hay etiquetas <font> sin fontName explícito");
    }

    @Test
    void compilaYGeneraUnPdf() throws Exception {
        byte[] pdf = JasperExportManager.exportReportToPdf(llenar());

        assertTrue(pdf.length > 1000, "PDF sospechosamente chico: " + pdf.length + " bytes");
        assertEquals("%PDF", new String(Arrays.copyOfRange(pdf, 0, 4), StandardCharsets.ISO_8859_1));
    }

    @Test
    void laBarraDelCdcEsUnSoloBloqueParejo() throws Exception {
        // Eran dos campos opacos sueltos: con una SansSerif ancha (DejaVu, la de un servidor sin
        // escritorio) el numero partia en dos lineas y crecia solo, dejando la barra en escalon y
        // tapando la leyenda de abajo. El fondo lo pone un unico frame y el alto no depende del texto.
        JRPrintFrame barra = barraDelCdc(llenar());

        assertEquals(ModeEnum.OPAQUE, barra.getModeValue(), "la barra no tiene fondo");
        assertEquals(2, barra.getElements().size(), "la barra lleva rótulo y número, nada más");
        for (JRPrintElement elemento : barra.getElements()) {
            JRPrintText texto = (JRPrintText) elemento;
            assertEquals(0, texto.getY(), "desalineado: " + texto.getFullText());
            assertEquals(barra.getHeight(), texto.getHeight(), "alto distinto al de la barra: " + texto.getFullText());
            assertEquals(ModeEnum.TRANSPARENT, texto.getModeValue(), "fondo propio: " + texto.getFullText());
            assertNull(texto.getTextTruncateIndex(), "texto recortado: " + texto.getFullText());
            assertTrue(texto.getTextHeight() <= barra.getHeight(), "no entra en una línea: " + texto.getFullText());
        }
        assertEquals("CDC:", ((JRPrintText) barra.getElements().get(0)).getFullText());
        assertEquals(CDC, ((JRPrintText) barra.getElements().get(1)).getFullText());
    }

    private static JRPrintFrame barraDelCdc(JasperPrint impreso) {
        for (JRPrintPage pagina : impreso.getPages()) {
            for (JRPrintElement elemento : pagina.getElements()) {
                if (elemento instanceof JRPrintFrame && contiene((JRPrintFrame) elemento, CDC)) {
                    return (JRPrintFrame) elemento;
                }
            }
        }
        return fail("el rótulo y el número del CDC no están dentro de un mismo frame");
    }

    private static boolean contiene(JRPrintFrame frame, String texto) {
        for (JRPrintElement elemento : frame.getElements()) {
            if (elemento instanceof JRPrintText && texto.equals(((JRPrintText) elemento).getFullText())) {
                return true;
            }
        }
        return false;
    }

    private static JasperPrint llenar() throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("razonSocial", "FRANCO AREVALOS S.A.");
        p.put("numeroFactura", "001-001-0000007");
        p.put("cdc", CDC);
        p.put("urlValidacion", "https://ekuatia.set.gov.py/consultas/");
        p.put("contado", Boolean.TRUE);
        p.put("credito", Boolean.FALSE);
        for (String monto : Arrays.asList("subtotalExentas", "subtotal5", "subtotal10", "totalOperacion",
                "totalIva5", "totalIva10", "totalIva", "totalFinal", "totalEnGuarani")) {
            p.put(monto, 110000d);
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", 1L);
        item.put("codigo", "123");
        item.put("descripcion", "PRODUCTO DE PRUEBA");
        item.put("iva", 10);
        item.put("descripcionPresentacion", "UNIDAD");
        item.put("cantidad", 1f);
        item.put("precioUnitario", 110000d);
        item.put("total", 110000d);
        List<Map<String, ?>> items = Collections.singletonList(item);

        return JasperFillManager.fillReport(JasperCompileManager.compileReport(RUTA), p,
                new JRMapCollectionDataSource(items));
    }
}
