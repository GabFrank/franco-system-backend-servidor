package com.franco.dev.service.impresion;

import com.franco.dev.domain.financiero.Moneda;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.franco.dev.service.impresion.ImpresionService.decimalesMoneda;
import static com.franco.dev.service.impresion.ImpresionService.formatPedido;
import static com.franco.dev.service.impresion.ImpresionService.patronCosto;
import static com.franco.dev.service.impresion.ImpresionService.patronMonto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF y ticket del pedido de compra con los decimales de la moneda (desktop #711): el pedido 42 en USD,
 * 24 x 0,8125, salia «P. Unit. 1 / Subtotal 20 / TOTAL 20».
 */
class ImpresionServicePedidoFormatoTest {

    private static Moneda moneda(Integer decimales, String denominacion) {
        Moneda m = new Moneda();
        m.setDecimales(decimales);
        m.setDenominacion(denominacion);
        return m;
    }

    @Test
    void decimalesDeLaMonedaYSinElDatoPorDenominacion() {
        assertEquals(2, decimalesMoneda(moneda(2, "DOLAR")));
        assertEquals(0, decimalesMoneda(moneda(0, "GUARANI")));
        assertEquals(0, decimalesMoneda(moneda(null, "GUARANI")));
        assertEquals(2, decimalesMoneda(moneda(null, "DOLAR")));
        assertEquals(0, decimalesMoneda(null));
    }

    @Test
    void ticketEnDolaresConElCostoDeCuatroDecimales() {
        assertEquals("0,8125", formatPedido(0.8125, patronCosto(2)));
        assertEquals("81,25", formatPedido(81.25, patronCosto(2)));
        assertEquals("19,50", formatPedido(24 * 0.8125, patronMonto(2)));
        assertEquals("1.234.567,50", formatPedido(1234567.5, patronMonto(2)));
    }

    @Test
    void ticketEnGuaraniesSinDecimalesComoAntes() {
        assertEquals("42.000", formatPedido(42000, patronCosto(0)));
        assertEquals("2.966", formatPedido(2966.4, patronCosto(0)));
        assertEquals("71.184", formatPedido(71184, patronMonto(0)));
        // Redondeo del Math.round de antes (HALF_UP), no el HALF_EVEN por defecto de DecimalFormat
        assertEquals("2.967", formatPedido(2966.5, patronMonto(0)));
    }

    @Test
    void pdfDelPedidoMuestraLosDecimalesDeLaMoneda(@TempDir Path dir) throws Exception {
        List<String> dolar = textosDelPdf(dir, 0.8125, 24.0, 2);
        assertTrue(dolar.contains("0,8125"), dolar.toString());
        assertTrue(dolar.contains("19,50"), dolar.toString());

        List<String> guarani = textosDelPdf(dir, 3500.0, 12.0, 0);
        assertTrue(guarani.contains("3.500"), guarani.toString());
        assertTrue(guarani.contains("42.000"), guarani.toString());
    }

    /** Compila y llena la plantilla real (se compila en runtime: el build no la valida). */
    private List<String> textosDelPdf(Path dir, double precio, double cantidad, int decimales) throws Exception {
        Path logo = dir.resolve("logo.png");
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", logo.toFile());

        JasperReport reporte;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("reports/pedido-compra.jrxml")) {
            reporte = JasperCompileManager.compileReport(in);
        }
        // El runner de CI no tiene Verdana; el test lee los textos, no mide la fuente
        reporte.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        ImpresionService.PedidoItemDto dto = new ImpresionService.PedidoItemDto();
        dto.setNumero(1);
        dto.setDescripcion("PILSEN CLASICA LATA 269 ML");
        dto.setPresentacion("Caja x 12 unid.");
        dto.setCantidadUnidades(cantidad);
        dto.setPrecioUnitario(precio);
        dto.setSubtotal(cantidad * precio);
        dto.setVencimiento("");

        Map<String, Object> parametros = new HashMap<>();
        parametros.put("pedidoId", 42L);
        parametros.put("qrText", "frc-0-PEDIDO-42");
        parametros.put("logo", logo.toString());
        parametros.put("montoTotal", cantidad * precio);
        parametros.put("patronCosto", patronCosto(decimales));
        parametros.put("patronMonto", patronMonto(decimales));
        parametros.put(JRParameter.REPORT_LOCALE, Locale.GERMANY);
        JasperPrint impreso = JasperFillManager.fillReport(reporte, parametros,
                new JRBeanCollectionDataSource(Collections.singletonList(dto)));

        List<String> textos = new ArrayList<>();
        for (JRPrintPage pagina : impreso.getPages()) {
            for (JRPrintElement elemento : pagina.getElements()) {
                if (elemento instanceof JRPrintText) {
                    textos.add(((JRPrintText) elemento).getFullText());
                }
            }
        }
        return textos;
    }
}
