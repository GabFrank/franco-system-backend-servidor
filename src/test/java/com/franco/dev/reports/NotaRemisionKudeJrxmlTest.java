package com.franco.dev.reports;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.ModalidadTransporteNr;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.financiero.enums.ResponsableEmisionNr;
import com.franco.dev.domain.financiero.enums.TipoTransporteNr;
import com.franco.dev.service.financiero.KudeNotaRemisionService;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El .jrxml se compila en runtime: un error de plantilla no lo ve el build ni el CI, revienta al
 * generar el PDF en producción. Este test compila, llena con datos dummy y exporta, que es el único
 * gate real. Y verifica la regla de fuentes del repo: solo SansSerif (lógica de Java, siempre
 * disponible); la plantilla de la referencia traía un Monospaced.
 */
class NotaRemisionKudeJrxmlTest {

    private static final String RUTA = "src/main/resources/reports/nota-remision-kude.jrxml";

    @Test
    void laPlantillaSoloUsaFuentesPermitidas() throws Exception {
        String jrxml = new String(Files.readAllBytes(Paths.get(RUTA)), StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("fontName=\"([^\"]*)\"").matcher(jrxml);
        int fuentes = 0;
        while (m.find()) {
            fuentes++;
            assertTrue(Arrays.asList("SansSerif", "Verdana").contains(m.group(1)),
                    "fuente no permitida en el KuDE: " + m.group(1));
        }
        assertTrue(fuentes > 0, "la plantilla tiene que declarar fontName explícito");
    }

    @Test
    void compilaYGeneraUnPdfConDatosDummy() throws Exception {
        KudeNotaRemisionService service = new KudeNotaRemisionService();

        String base64 = service.generarPdfBase64(nota(), items(), timbrado(), documentoElectronico());

        assertNotNull(base64);
        byte[] pdf = Base64.getDecoder().decode(base64);
        assertTrue(pdf.length > 1000, "el PDF salió sospechosamente chico: " + pdf.length + " bytes");
        assertEquals("%PDF", new String(Arrays.copyOfRange(pdf, 0, 4), StandardCharsets.ISO_8859_1));
    }

    @Test
    void elNumeroYLosEnumsSalenLegiblesEnLosParametros() {
        KudeNotaRemisionService service = new KudeNotaRemisionService();

        var parametros = service.parametros(nota(), timbrado(), documentoElectronico());

        assertEquals("001-001-0000042", parametros.get("numeroNotaRemision"));
        assertEquals("Traslado entre locales", parametros.get("motivoEmision"));
        assertEquals("Terrestre", parametros.get("modalidadTransporte"));
        assertEquals("MERCEDES BENZ", parametros.get("vehiculoMarca"),
                "el KuDE muestra la marca completa; la abreviatura es solo para el XML");
    }

    @Test
    void elTextoDeLosCamposRealmenteSeRenderiza() throws Exception {
        // Un campo con alto insuficiente sale VACIO sin que Jasper falle, y el PDF igual pesa:
        // por eso se mira el texto renderizado y no el tamaño del archivo.
        KudeNotaRemisionService service = new KudeNotaRemisionService();

        String texto = textoDe(service.llenar(nota(), items(), timbrado(), documentoElectronico()));

        assertTrue(texto.contains("001-001-0000042"), "falta el número de la nota");
        assertTrue(texto.contains("FRANCO SA"), "falta la razón social");
        assertTrue(texto.contains("80012345-6"), "falta el RUC");
        assertTrue(texto.contains("Traslado entre locales"), "falta el motivo del traslado");
        assertTrue(texto.contains("ABC123"), "falta la matrícula del vehículo");
        assertTrue(texto.contains("JUAN PEREZ"), "falta el chofer");
        assertTrue(texto.contains("CAJA DE GASEOSAS 2L"), "falta el primer ítem");
        assertTrue(texto.contains("BOLSA DE AZUCAR 1KG"), "falta el segundo ítem");
        assertTrue(texto.contains("Mercadería"), "falta el título de la sección de ítems");
        for (String columna : Arrays.asList("Código", "Unidad", "Cantidad", "Descripción")) {
            assertTrue(texto.contains(columna), "falta la cabecera de columna " + columna);
        }
        assertTrue(texto.contains("01800123456001001000004212026091712345678901"), "falta el CDC");
    }

    private static String textoDe(JasperPrint impreso) {
        StringBuilder sb = new StringBuilder();
        for (JRPrintPage pagina : impreso.getPages()) {
            for (JRPrintElement elemento : pagina.getElements()) {
                if (elemento instanceof JRPrintText) {
                    sb.append(((JRPrintText) elemento).getFullText()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    @Test
    void elTelefonoImpresoEsElMismoQueViajaEnElXml() {
        // Sin telefono en el detalle, el XML lleva el de la cabecera (SifenTimbradoHelper): el KuDE
        // tiene que mostrar ese mismo, no quedar vacio.
        KudeNotaRemisionService service = new KudeNotaRemisionService();
        TimbradoDetalle detalle = timbrado();
        detalle.setTelefono(null);
        detalle.getTimbrado().setTelefono("0982700027");

        var p = service.parametros(nota(), detalle, documentoElectronico());

        assertEquals("0982700027", p.get("telefonoEmisor"));
    }

    @Test
    void enTransportePropioElTransportistaEsElEmisor() {
        // Salia "Transportista: (RUC: )" vacio: en transporte propio la nota no guarda
        // transportista porque es la propia empresa, igual que en el XML que se firma.
        KudeNotaRemisionService service = new KudeNotaRemisionService();
        NotaRemision nota = nota();
        nota.setTransportistaNombre(null);
        nota.setTransportistaRuc(null);

        var p = service.parametros(nota, timbrado(), documentoElectronico());

        assertEquals("FRANCO SA", p.get("transportistaNombre"));
        assertEquals("80012345-6", p.get("transportistaRuc"));
    }

    @Test
    void conTransportistaCargadoNoSePisaConElEmisor() {
        KudeNotaRemisionService service = new KudeNotaRemisionService();
        NotaRemision nota = nota();
        nota.setTransportistaNombre("FLETES DEL ESTE SRL");
        nota.setTransportistaRuc("80055555-1");

        var p = service.parametros(nota, timbrado(), documentoElectronico());

        assertEquals("FLETES DEL ESTE SRL", p.get("transportistaNombre"));
        assertEquals("80055555-1", p.get("transportistaRuc"));
    }

    @Test
    void sinDocumentoElectronicoTodaviaGeneraElPdf() throws Exception {
        // Una nota recién creada, antes de mandarla a SIFEN, también se puede imprimir.
        KudeNotaRemisionService service = new KudeNotaRemisionService();

        String base64 = service.generarPdfBase64(nota(), items(), timbrado(), null);

        assertTrue(Base64.getDecoder().decode(base64).length > 1000);
    }

    private static NotaRemision nota() {
        NotaRemision nota = new NotaRemision();
        nota.setId(1L);
        nota.setSucursalId(1L);
        nota.setTimbradoDetalleId(10L);
        nota.setNumeroNotaRemision(42);
        nota.setFecha(LocalDateTime.now());
        nota.setOrigen(OrigenNotaRemision.TRANSFERENCIA);
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_ENTRE_LOCALES);
        nota.setResponsableEmision(ResponsableEmisionNr.EMISOR_FACTURA);
        nota.setKmEstimado(25);
        nota.setFechaInicioTraslado(LocalDate.now());
        nota.setReceptorNombre("FRANCO SA");
        nota.setReceptorRuc("80012345-6");
        nota.setSalidaDireccion("DEPOSITO CENTRAL");
        nota.setSalidaCiudad("ASUNCION");
        nota.setSalidaDepartamento("CAPITAL");
        nota.setEntregaDireccion("SUCURSAL SAN LORENZO");
        nota.setEntregaCiudad("SAN LORENZO");
        nota.setEntregaDepartamento("CENTRAL");
        nota.setTipoTransporte(TipoTransporteNr.PROPIO);
        nota.setModalidadTransporte(ModalidadTransporteNr.TERRESTRE);
        nota.setVehiculoMarca("MERCEDES BENZ");
        nota.setVehiculoMatricula("ABC123");
        nota.setChoferNombre("JUAN PEREZ");
        nota.setChoferDocumento("1234567");
        nota.setActivo(true);
        return nota;
    }

    private static List<NotaRemisionItem> items() {
        NotaRemisionItem uno = new NotaRemisionItem();
        uno.setCodigo("001");
        uno.setDescripcion("CAJA DE GASEOSAS 2L");
        uno.setCantidad(new BigDecimal("12"));
        uno.setUnidadMedida("UNI");

        NotaRemisionItem dos = new NotaRemisionItem();
        dos.setCodigo("002");
        dos.setDescripcion("BOLSA DE AZUCAR 1KG");
        dos.setCantidad(new BigDecimal("40"));
        dos.setUnidadMedida("kg");
        return Arrays.asList(uno, dos);
    }

    private static TimbradoDetalle timbrado() {
        Timbrado timbrado = new Timbrado();
        timbrado.setNumero("12345678");
        timbrado.setRuc("80012345-6");
        timbrado.setRazonSocial("FRANCO SA");
        timbrado.setEmail("facturacion@franco.com.py");
        timbrado.setFechaInicio(LocalDateTime.now().minusMonths(6));
        timbrado.setDescActividadEconomicaPrincipal("VENTA AL POR MENOR");

        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setId(10L);
        detalle.setSucursalId(1L);
        detalle.setTimbrado(timbrado);
        detalle.setPuntoExpedicion("1");
        detalle.setDireccion("AVDA ESPAÑA 123");
        detalle.setCiudad("ASUNCION");
        detalle.setDepartamento("CAPITAL");
        detalle.setTelefono("021123456");
        return detalle;
    }

    private static DocumentoElectronico documentoElectronico() {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setCdc("01800123456001001000004212026091712345678901");
        de.setUrlQr("https://ekuatia.set.gov.py/consultas/qr?nVersion=150&Id=018001234560010010000042");
        return de;
    }
}
