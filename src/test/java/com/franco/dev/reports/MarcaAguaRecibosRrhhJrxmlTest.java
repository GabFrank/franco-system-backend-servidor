package com.franco.dev.reports;

import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRPrintLine;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.type.ModeEnum;
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
 * Marca de agua de los cuatro documentos A4 de RRHH: sale cuando viene, no rompe cuando no viene, va
 * dentro de cada via (el sueldo lleva dos) y, como es de fondo, no cambia el tamaño del recibo.
 */
class MarcaAguaRecibosRrhhJrxmlTest {

    /** Plantilla y cantidad de marcas por hoja: el sueldo lleva una por via. */
    static final Object[][] PLANTILLAS = {
            {"reports/recibo-rrhh.jrxml", 1},
            {"reports/recibo-liquidacion.jrxml", 2},
            {"reports/recibo-finiquito.jrxml", 1},
            {"reports/acta-advertencia.jrxml", 1}
    };

    private static final String EMPRESA = "COMERCIAL FRANCO AREVALOS S.A.";          // 30
    private static final String DIRECCION = "AVENIDA GRAL BERNARDINO CABALLERO C/ COLONIA CANINDEYU 1234"; // 60
    private static final String OBSERVACION_LARGA = "SE LE ADELANTO PARTE DEL SUELDO POR TRANSFERENCIA EL 15/11"
            + " Y EL RESTO SE ENTREGA EN EFECTIVO; LA CUOTA DEL CREDITO SE DIFIERE AL MES SIGUIENTE POR PEDIDO DEL"
            + " ENCARGADO DE SUCURSAL";

    static BufferedImage imagen() {
        return new BufferedImage(400, 242, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void conMarcaDeAguaLaImprimeUnaVezPorViaYExportaAPdf() throws Exception {
        for (Object[] t : PLANTILLAS) {
            String tpl = (String) t[0];
            JasperPrint print = llenar(tpl, params(imagen()), 3);

            assertEquals(1, print.getPages().size(), tpl);
            assertEquals(t[1], imagenes(print), tpl + ": marcas de agua por hoja");
            assertTrue(JasperExportManager.exportReportToPdf(print).length > 0, tpl);
        }
    }

    /** La via ORIGINAL del sueldo tiene su propia marca, debajo de la linea de corte. */
    @Test
    void enElSueldoCadaViaTieneSuMarca() throws Exception {
        JasperPrint print = llenar("reports/recibo-liquidacion.jrxml", params(imagen()), 3);
        int corte = -1;
        List<Integer> marcas = new ArrayList<>();
        for (JRPrintElement e : print.getPages().get(0).getElements()) {
            if (e instanceof JRPrintText && ((JRPrintText) e).getFullText().contains("cortar por aqui")) corte = e.getY();
            if (e instanceof JRPrintImage) marcas.add(e.getY());
        }
        assertTrue(corte > 0, "no se encontro la linea de corte");
        int arriba = 0, abajo = 0;
        for (int y : marcas) {
            if (y < corte) arriba++;
            else abajo++;
        }
        assertEquals(1, arriba, "una marca arriba del corte");
        assertEquals(1, abajo, "una marca abajo del corte");
    }

    /**
     * Un elemento opaco encima de la marca la tapa: el recuadro de datos del sueldo es un rectangle, y
     * en Jasper se pinta con fondo blanco por defecto. Salia la marca cortada por la cabecera.
     */
    @Test
    void ningunElementoOpacoTapaLaMarca() throws Exception {
        for (Object[] t : PLANTILLAS) {
            String tpl = (String) t[0];
            List<JRPrintElement> elementos = print(tpl).getPages().get(0).getElements();
            for (JRPrintElement marca : elementos) {
                if (!(marca instanceof JRPrintImage)) continue;
                for (JRPrintElement e : elementos) {
                    // Una linea tambien figura OPAQUE, pero no tiene relleno: no tapa nada.
                    if (e == marca || e instanceof JRPrintLine || e.getModeValue() != ModeEnum.OPAQUE) continue;
                    boolean seTocan = e.getX() < marca.getX() + marca.getWidth() && marca.getX() < e.getX() + e.getWidth()
                            && e.getY() < marca.getY() + marca.getHeight() && marca.getY() < e.getY() + e.getHeight();
                    assertFalse(seTocan, tpl + ": un " + e.getClass().getSimpleName() + " opaco en (" + e.getX() + ","
                            + e.getY() + ") tapa la marca de agua");
                }
            }
        }
    }

    private static JasperPrint print(String tpl) throws Exception {
        return llenar(tpl, params(imagen()), 3);
    }

    @Test
    void sinMarcaDeAguaSaleIgualYSinImagen() throws Exception {
        for (Object[] t : PLANTILLAS) {
            String tpl = (String) t[0];
            Map<String, Object> p = params(null);
            JasperPrint print = llenar(tpl, p, 3);
            assertEquals(0, imagenes(print), tpl);
            assertTrue(JasperExportManager.exportReportToPdf(print).length > 0, tpl);

            p.remove("marcaAgua");   // los llamadores viejos no mandan el parametro
            assertEquals(0, imagenes(llenar(tpl, p, 3)), tpl);
        }
    }

    /** El encabezado es el de antes: la empresa y la linea RUC/direccion/telefono salen enteras. */
    @Test
    void elEncabezadoNoSeRecorta() throws Exception {
        for (Object[] t : PLANTILLAS) {
            String tpl = (String) t[0];
            String textos = ReciboRrhhJrxmlTest.textosDelPrint(llenar(tpl, params(imagen()), 3));

            assertTrue(textos.contains(EMPRESA), tpl + " recorta la empresa: " + textos);
            if (tpl.contains("liquidacion") || tpl.contains("acta")) {
                // getFullText() ya viene recortado: se busca el texto entero.
                assertTrue(textos.contains(DIRECCION + "   -   Tel: 0982700027"),
                        tpl + " recorta la linea RUC/direccion/telefono: " + textos);
            }
        }
    }

    /**
     * La marca es de fondo y no mueve el techo de una hoja del sueldo: 23 items
     * (802 utiles - (110 + 18 + 328) = 346, a 15 pt por fila). Eran 25 hasta que el summary
     * crecio de 292 a 328 para dejar lugar a la firma en las dos vias.
     */
    @Test
    void liquidacionTechoDeUnaHoja() throws Exception {
        assertEquals(1, llenar("reports/recibo-liquidacion.jrxml", params(imagen()), 23).getPages().size(),
                "23 items tienen que entrar en una hoja");
        assertEquals(2, llenar("reports/recibo-liquidacion.jrxml", params(imagen()), 24).getPages().size(),
                "con 24 items ya no entra: si esto cambia, actualizar el techo documentado");
    }

    /** Techo medido del finiquito: 21 conceptos, el mismo que sin marca de agua (con observacion larga o corta). */
    @Test
    void finiquitoTechoDeUnaHoja() throws Exception {
        assertEquals(1, llenar("reports/recibo-finiquito.jrxml", params(imagen()), 21).getPages().size(),
                "21 conceptos tienen que entrar en una hoja");
        assertEquals(2, llenar("reports/recibo-finiquito.jrxml", params(imagen()), 22).getPages().size(),
                "con 22 conceptos ya no entra: si esto cambia, actualizar el techo");
    }

    // ===== helpers =====

    private static Map<String, Object> params(BufferedImage marcaAgua) {
        Map<String, Object> p = new HashMap<>();
        p.put("empresa", EMPRESA);
        p.put("ruc", "80012345-6");
        p.put("direccionEmpresa", DIRECCION);
        p.put("telefonoEmpresa", "0982700027");
        p.put("numero", "486");
        p.put("observacion", OBSERVACION_LARGA);
        p.put("marcaAgua", marcaAgua);
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
}
