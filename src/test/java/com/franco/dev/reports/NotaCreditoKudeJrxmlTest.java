package com.franco.dev.reports;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.domain.financiero.NotaCreditoItem;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaCredito;
import com.franco.dev.service.financiero.KudeNotaCreditoService;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El .jrxml se compila en runtime: este test es el único gate. Se verifica el TEXTO renderizado, no
 * el peso del PDF — un campo con alto insuficiente sale vacío sin que Jasper falle.
 */
class NotaCreditoKudeJrxmlTest {

    private static final String RUTA = "src/main/resources/reports/nota-credito-kude.jrxml";
    private static final String CDC_NOTA = "05800994825001001000000122026091812345678901";
    private static final String CDC_FACTURA = "01800994825001001000000122026091512345678901";

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
        // La plantilla de frc-efact no declaraba ninguna: todas las <font> tienen que llevar nombre.
        int etiquetas = jrxml.split("<font", -1).length - 1;
        assertEquals(etiquetas, fuentes, "hay etiquetas <font> sin fontName explícito");
    }

    @Test
    void compilaYGeneraUnPdf() throws Exception {
        KudeNotaCreditoService service = new KudeNotaCreditoService();

        String base64 = service.generarPdfBase64(nota(null, null), items(), timbrado(),
                documentoElectronico(), CDC_FACTURA);

        byte[] pdf = Base64.getDecoder().decode(base64);
        assertTrue(pdf.length > 1000, "PDF sospechosamente chico: " + pdf.length + " bytes");
        assertEquals("%PDF", new String(Arrays.copyOfRange(pdf, 0, 4), StandardCharsets.ISO_8859_1));
    }

    @Test
    void elTextoDeLosCamposRealmenteSeRenderiza() throws Exception {
        KudeNotaCreditoService service = new KudeNotaCreditoService();

        String texto = textoDe(service.llenar(nota(null, null), items(), timbrado(),
                documentoElectronico(), CDC_FACTURA));

        // El campo del numero tenia alto 12 con fuente 9 en negrita y Jasper lo recortaba hasta
        // dejarlo VACIO, sin error: con alto 14 entra. Es el caso que este test existe para atrapar.
        assertTrue(texto.contains("001-001-0000007"), "falta el número de la nota");
        assertTrue(texto.contains("FRANCO AREVALOS S.A."), "falta la razón social del emisor");
        assertTrue(texto.contains("CLIENTE SA"), "falta el cliente");
        assertTrue(texto.contains("80012345-6"), "falta el RUC del cliente");
        assertTrue(texto.contains("Devolucion"), "falta el motivo: " + texto.substring(0, 200));
        assertTrue(texto.contains("PRODUCTO DE PRUEBA"), "falta el ítem");
        assertTrue(texto.contains(CDC_NOTA), "falta el CDC de la nota");
        assertTrue(texto.contains(CDC_FACTURA), "falta el CDC de la factura acreditada");
        // Bloque de consulta igual al de la nota de remisión.
        assertTrue(texto.contains("Consulte esta Nota de Crédito"), "falta el título del bloque de consulta");
        assertTrue(texto.contains("https://ekuatia.set.gov.py/consultas/"), "falta la URL de consulta");
        assertTrue(texto.contains("CDC (Código de Control):"), "falta el rótulo del CDC");
        assertTrue(texto.contains("solicitar la cancelación dentro de las 48 horas"), "falta la leyenda completa");
    }

    @Test
    void enMonedaExtranjeraSeInformaLaMonedaYElCambio() {
        KudeNotaCreditoService service = new KudeNotaCreditoService();

        var p = service.parametros(nota("USD", new BigDecimal("7300")), timbrado(),
                documentoElectronico(), CDC_FACTURA);

        assertEquals(Boolean.TRUE, p.get("tieneMonedaExtranjera"));
        assertEquals("USD", p.get("moneda"));
        assertEquals("7300", p.get("tipoCambio"));
    }

    @Test
    void elTelefonoImpresoEsElMismoQueViajaEnElXml() {
        KudeNotaCreditoService service = new KudeNotaCreditoService();
        TimbradoDetalle detalle = timbrado();
        detalle.setTelefono("  ");
        detalle.getTimbrado().setTelefono("0982700027");

        var p = service.parametros(nota(null, null), detalle, documentoElectronico(), CDC_FACTURA);

        assertEquals("0982700027", p.get("telefonoEmisor"));
    }

    @Test
    void enGuaraniesNoSeInformaTipoDeCambio() {
        KudeNotaCreditoService service = new KudeNotaCreditoService();

        var p = service.parametros(nota(null, null), timbrado(), documentoElectronico(), CDC_FACTURA);

        assertEquals(Boolean.FALSE, p.get("tieneMonedaExtranjera"));
        assertEquals("GS", p.get("moneda"));
        assertEquals("", p.get("tipoCambio"));
        assertEquals("001-001-0000007", p.get("numeroNotaCredito"));
    }

    @Test
    void cadaItemCaeEnLaColumnaDeSuTasaDeIva() {
        NotaCreditoItem diez = item(10, "110000");
        NotaCreditoItem cinco = item(5, "105000");
        NotaCreditoItem exento = item(0, "50000");

        assertEquals(110000d, new KudeNotaCreditoService.ItemKude(diez).getMontoGravado10());
        assertEquals(0d, new KudeNotaCreditoService.ItemKude(diez).getMontoGravado5());
        assertEquals(105000d, new KudeNotaCreditoService.ItemKude(cinco).getMontoGravado5());
        assertEquals(50000d, new KudeNotaCreditoService.ItemKude(exento).getMontoExento());
        assertEquals(0d, new KudeNotaCreditoService.ItemKude(exento).getMontoGravado10());
    }

    private static String textoDe(JasperPrint impreso) {
        StringBuilder sb = new StringBuilder();
        for (JRPrintPage pagina : impreso.getPages()) {
            recolectar(pagina.getElements(), sb);
        }
        return sb.toString();
    }

    /** Recursivo: un campo dentro de un frame no aparece en los elementos de la página. */
    private static void recolectar(java.util.List<JRPrintElement> elementos, StringBuilder sb) {
        for (JRPrintElement elemento : elementos) {
            if (elemento instanceof JRPrintText) {
                sb.append(((JRPrintText) elemento).getFullText()).append('\n');
            } else if (elemento instanceof net.sf.jasperreports.engine.JRPrintFrame) {
                recolectar(((net.sf.jasperreports.engine.JRPrintFrame) elemento).getElements(), sb);
            }
        }
    }

    private static NotaCredito nota(String moneda, BigDecimal cambio) {
        NotaCredito nota = new NotaCredito();
        nota.setId(1L);
        nota.setSucursalId(1L);
        nota.setTimbradoDetalleId(10L);
        nota.setNumeroNotaCredito(7);
        nota.setFecha(LocalDateTime.now());
        nota.setFacturaLegalId(300L);
        nota.setMotivoEmision(MotivoEmisionNotaCredito.DEVOLUCION);
        nota.setDescripcionMotivo("Devolución de mercadería en mal estado");
        nota.setNombre("CLIENTE SA");
        nota.setRuc("80012345-6");
        nota.setDireccion("AVDA ESPAÑA 123");
        nota.setMonedaExtranjera(moneda);
        nota.setTipoCambio(cambio);
        nota.setIvaParcial10(new BigDecimal("10000"));
        nota.setTotalParcial10(new BigDecimal("110000"));
        nota.setTotalFinal(new BigDecimal("110000"));
        nota.setActivo(true);
        return nota;
    }

    private static List<NotaCreditoItem> items() {
        return Arrays.asList(item(10, "110000"));
    }

    private static NotaCreditoItem item(Integer iva, String total) {
        NotaCreditoItem item = new NotaCreditoItem();
        item.setFacturaLegalItemId(99L);
        item.setDescripcion("PRODUCTO DE PRUEBA");
        item.setCantidad(BigDecimal.ONE);
        item.setPrecioUnitario(new BigDecimal(total));
        item.setTotal(new BigDecimal(total));
        item.setIva(iva);
        return item;
    }

    private static TimbradoDetalle timbrado() {
        Timbrado timbrado = new Timbrado();
        timbrado.setNumero("18270044");
        timbrado.setRuc("80099482-5");
        timbrado.setRazonSocial("FRANCO AREVALOS S.A.");
        timbrado.setEmail("facturacion@franco.com.py");
        timbrado.setFechaInicio(LocalDateTime.now().minusMonths(6));
        timbrado.setDescActividadEconomicaPrincipal("VENTA AL POR MENOR");

        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setId(10L);
        detalle.setSucursalId(1L);
        detalle.setTimbrado(timbrado);
        detalle.setPuntoExpedicion("1");
        detalle.setDireccion("AVDA ESPAÑA 123");
        detalle.setCiudad("SALTO DEL GUAIRA");
        detalle.setDepartamento("CANINDEYU");
        detalle.setTelefono("021123456");
        return detalle;
    }

    private static DocumentoElectronico documentoElectronico() {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc(CDC_NOTA);
        de.setUrlQr("https://ekuatia.set.gov.py/consultas-test/qr?nVersion=150&Id=" + CDC_NOTA);
        return de;
    }
}
