package com.franco.dev.reports;

import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El logo de los cuatro documentos A4 de RRHH: sale cuando viene, no rompe cuando no viene, y el
 * lugar que ocupa no recorta el encabezado ni saca el recibo de una hoja.
 *
 * <p>Los largos son los de la base real con margen: la razon social mas larga de timbrado tiene 20
 * caracteres y la direccion mas larga 52 (bodega local, 2026-09-29).</p>
 */
class LogoRecibosRrhhJrxmlTest {

    static final String[] PLANTILLAS = {
            "reports/recibo-rrhh.jrxml",
            "reports/recibo-liquidacion.jrxml",
            "reports/recibo-finiquito.jrxml",
            "reports/acta-advertencia.jrxml"
    };

    private static final String EMPRESA = "COMERCIAL FRANCO AREVALOS S.A.";          // 30
    private static final String DIRECCION = "AVENIDA GRAL BERNARDINO CABALLERO C/ COLONIA CANINDEYU 1234"; // 60
    private static final String OBSERVACION_LARGA = "SE LE ADELANTO PARTE DEL SUELDO POR TRANSFERENCIA EL 15/11"
            + " Y EL RESTO SE ENTREGA EN EFECTIVO; LA CUOTA DEL CREDITO SE DIFIERE AL MES SIGUIENTE POR PEDIDO DEL"
            + " ENCARGADO DE SUCURSAL";

    static BufferedImage logo() {
        return new BufferedImage(400, 242, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void conLogoLoImprimeYExportaAPdf() throws Exception {
        for (String tpl : PLANTILLAS) {
            JasperPrint print = llenar(tpl, params(logo()), 3);

            assertEquals(1, imagenes(print), tpl + ": tiene que salir el logo, una vez");
            assertTrue(JasperExportManager.exportReportToPdf(print).length > 0, tpl);
        }
    }

    @Test
    void sinLogoSaleIgualYSinImagen() throws Exception {
        for (String tpl : PLANTILLAS) {
            Map<String, Object> p = params(null);
            JasperPrint print = llenar(tpl, p, 3);
            assertEquals(0, imagenes(print), tpl);
            assertTrue(JasperExportManager.exportReportToPdf(print).length > 0, tpl);

            p.remove("logo");   // los llamadores viejos no mandan el parametro
            assertEquals(0, imagenes(llenar(tpl, p, 3)), tpl);
        }
    }

    /**
     * El encabezado se corrio para dejarle lugar al logo: nada de lo que se movio sale recortado.
     * Jasper no avisa el recorte: getFullText() ya viene cortado, asi que se busca el texto entero.
     */
    @Test
    void elEncabezadoCorridoNoSeRecorta() throws Exception {
        for (String tpl : PLANTILLAS) {
            JasperPrint print = llenar(tpl, params(logo()), 3);
            String textos = ReciboRrhhJrxmlTest.textosDelPrint(print);

            assertTrue(textos.contains(EMPRESA), tpl + " recorta la empresa: " + textos);
            boolean llevaDireccion = tpl.contains("liquidacion") || tpl.contains("acta");
            if (llevaDireccion) {
                assertTrue(textos.contains(DIRECCION + "   -   Tel: 0982700027"),
                        tpl + " recorta la linea RUC/direccion/telefono: " + textos);
            }
        }
    }

    /**
     * El sueldo lleva las dos vias en una hoja. Con el logo la banda del titulo crecio 14 pt y el techo
     * bajo de 25 a 24 items: 802 utiles - (124 + 18 + 292) = 368, a 15 pt por fila.
     */
    @Test
    void liquidacionTechoDeUnaHoja() throws Exception {
        assertEquals(1, llenar("reports/recibo-liquidacion.jrxml", params(logo()), 24).getPages().size(),
                "24 items tienen que entrar en una hoja");
        assertEquals(2, llenar("reports/recibo-liquidacion.jrxml", params(logo()), 25).getPages().size(),
                "con 25 items ya no entra: si esto cambia, actualizar el techo documentado");
    }

    /**
     * El finiquito no tenia gate de una hoja. Con el logo la banda del titulo crecio 20 pt y el techo
     * medido bajo de 21 a 20 conceptos con observacion corta, y es 19 con la observacion larga de aca.
     * Un finiquito real lleva menos de 10 (salario, vacaciones, aguinaldo, preaviso, IPS...).
     */
    @Test
    void finiquitoTechoDeUnaHoja() throws Exception {
        assertEquals(1, llenar("reports/recibo-finiquito.jrxml", params(logo()), 19).getPages().size(),
                "19 conceptos tienen que entrar en una hoja");
        assertEquals(2, llenar("reports/recibo-finiquito.jrxml", params(logo()), 20).getPages().size(),
                "con 20 conceptos y observacion larga ya no entra: si esto cambia, actualizar el techo");
    }

    // ===== helpers =====

    private static Map<String, Object> params(BufferedImage logo) {
        Map<String, Object> p = new HashMap<>();
        p.put("empresa", EMPRESA);
        p.put("ruc", "80012345-6");
        p.put("direccionEmpresa", DIRECCION);
        p.put("telefonoEmpresa", "0982700027");
        p.put("numero", "486");
        p.put("observacion", OBSERVACION_LARGA);
        p.put("logo", logo);
        return p;
    }

    /** Llena la plantilla con n filas: todo parametro String sin valor lleva un dummy. */
    static JasperPrint llenar(String tpl, Map<String, Object> base, int filas) throws Exception {
        JasperReport jr = JasperCompileManager.compileReport(
                ResourceUtils.getFile("classpath:" + tpl).getAbsolutePath());
        Map<String, Object> p = new HashMap<>(base);
        for (JRParameter par : jr.getParameters()) {
            if (!par.isSystemDefined() && par.getValueClass() == String.class && !p.containsKey(par.getName())) {
                p.put(par.getName(), "DATO DUMMY");
            }
        }
        Collection<Map<String, ?>> rows = new ArrayList<>();
        for (int i = 1; i <= filas; i++) {
            Map<String, Object> row = new HashMap<>();
            for (JRField f : jr.getFields() != null ? jr.getFields() : new JRField[0]) {
                row.put(f.getName(), f.getValueClass() == String.class ? "CONCEPTO " + i : null);
            }
            rows.add(row);
        }
        return JasperFillManager.fillReport(jr, p, new JRMapCollectionDataSource(rows));
    }

    static int imagenes(JasperPrint print) {
        int n = 0;
        for (JRPrintPage pagina : print.getPages()) {
            for (JRPrintElement e : pagina.getElements()) {
                if (e instanceof JRPrintImage) n++;
            }
        }
        return n;
    }

    static List<JRPrintText> textosDe(JasperPrint print) {
        List<JRPrintText> r = new ArrayList<>();
        for (JRPrintPage pagina : print.getPages()) {
            for (JRPrintElement e : pagina.getElements()) {
                if (e instanceof JRPrintText) r.add((JRPrintText) e);
            }
        }
        return r;
    }
}
