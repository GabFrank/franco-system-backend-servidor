package com.franco.dev.reports;

import com.franco.dev.service.rrhh.dto.ReciboLiquidacionItemDto;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Valida que recibo-liquidacion.jrxml compila, rellena y exporta a PDF.
 *
 * <p>Era el unico de los tres recibos de RRHH sin test (finiquito y el generico si lo
 * tenian). Los .jrxml se compilan en runtime, asi que un UUID duplicado al copiar un
 * bloque o un parentesis desbalanceado en una expresion no revienta en el build ni en
 * CI: revienta al generar el PDF en produccion.</p>
 */
public class ReciboLiquidacionJrxmlTest {

    /** Los parametros que le pasa ReciboLiquidacionService.generarBase64. */
    private static final String[] PARAMS = {
            "empresa", "funcionario", "cargo", "documento", "direccion", "periodo",
            "fecha", "ciudad", "sueldoBase", "totalRecibido", "totalDescontado",
            "totalNeto", "montoEnLetras", "ruc", "direccionEmpresa", "telefonoEmpresa"
    };

    private static Map<String, Object> paramsDummy() {
        Map<String, Object> p = new HashMap<>();
        for (String k : PARAMS) p.put(k, "DATO DUMMY");
        return p;
    }

    @Test
    void compilaRellenaYExporta() throws Exception {
        List<ReciboLiquidacionItemDto> filas = Arrays.asList(
                new ReciboLiquidacionItemDto("SUELDO", "ENTRADA", "SALARIO BASE", "30/11/2026", "3.100.000"),
                new ReciboLiquidacionItemDto("IPS", "SALIDA", "DESCUENTO IPS", "30/11/2026", "279.000"),
                // Desglose de penalizaciones: una fila por penalizacion, con "TIPO: descripcion".
                new ReciboLiquidacionItemDto("DESCUENTOS", "SALIDA",
                        "QUEJA_CLIENTE: cliente reclamo por trato en caja 3", "05/11/2026", "120.000"),
                new ReciboLiquidacionItemDto("DESCUENTOS", "SALIDA",
                        "DANIO_MATERIAL: rotura de lector de codigo", "18/11/2026", "90.000"));

        byte[] pdf = generar(filas);
        org.junit.jupiter.api.Assertions.assertTrue(pdf != null && pdf.length > 0,
                "el PDF del recibo salio vacio");
    }

    /**
     * Una liquidacion con muchos items tiene que seguir generando. Importa porque el
     * recibo lleva dos vias al pie y el alto util de una A4 es finito: si el detalle
     * crece, Jasper no parte la banda sino que la manda entera a una hoja nueva.
     */
    @Test
    void soportaUnDetalleLargo() throws Exception {
        List<ReciboLiquidacionItemDto> filas = new ArrayList<>();
        filas.add(new ReciboLiquidacionItemDto("SUELDO", "ENTRADA", "SALARIO BASE", "30/11/2026", "3.100.000"));
        for (int i = 1; i <= 25; i++) {
            filas.add(new ReciboLiquidacionItemDto("CREDITO", "SALIDA",
                    "CUOTA CREDITO - venta #" + i, "1" + (i % 9) + "/11/2026", "50.000"));
        }
        byte[] pdf = generar(filas);
        org.junit.jupiter.api.Assertions.assertTrue(pdf != null && pdf.length > 0,
                "el recibo con detalle largo salio vacio");
    }

    /** Jasper omite la banda de detalle si el datasource viene vacio; no debe romper. */
    @Test
    void soportaDatasourceVacio() throws Exception {
        byte[] pdf = generar(new ArrayList<>());
        org.junit.jupiter.api.Assertions.assertTrue(pdf != null && pdf.length > 0,
                "el recibo sin items salio vacio");
    }

    private byte[] generar(List<ReciboLiquidacionItemDto> filas) throws Exception {
        return JasperExportManager.exportReportToPdf(llenar(filas));
    }

    private JasperPrint llenar(List<ReciboLiquidacionItemDto> filas) throws Exception {
        File f = ResourceUtils.getFile("classpath:reports/recibo-liquidacion.jrxml");
        JasperReport jr = JasperCompileManager.compileReport(f.getAbsolutePath());
        return JasperFillManager.fillReport(jr, paramsDummy(), new JRBeanCollectionDataSource(filas));
    }

    /**
     * El pedido es que las DOS vias entren en la misma hoja. Jasper no parte la banda
     * summary: si no entra, la manda entera a una pagina nueva y el recibo sale en dos
     * hojas. Esta es la unica forma de que ese limite no se descubra recien imprimiendo.
     *
     * <p><b>Techo medido: 27 items.</b> A4 deja 802pt utiles; title (110) + columnHeader
     * (18) + summary con las dos vias (256) = 384 fijos, y quedan 418 para el detalle a
     * 15pt por fila. Con 28 items se parte en dos hojas, verificado. El test corre hasta
     * 22 para dejar margen: si alguien agranda una banda, salta aca y no en la impresora.</p>
     */
    @Test
    void lasDosViasEntranEnUnaHoja() throws Exception {
        for (int n : new int[]{1, 5, 10, 15, 20, 22}) {
            List<ReciboLiquidacionItemDto> filas = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                filas.add(new ReciboLiquidacionItemDto("CREDITO", "SALIDA",
                        "CUOTA CREDITO - venta #" + i, "10/11/2026", "50.000"));
            }
            int paginas = llenar(filas).getPages().size();
            org.junit.jupiter.api.Assertions.assertEquals(1, paginas,
                    "con " + n + " items el recibo salio en " + paginas + " hojas; las dos vias tienen que entrar en una");
        }
    }
}
