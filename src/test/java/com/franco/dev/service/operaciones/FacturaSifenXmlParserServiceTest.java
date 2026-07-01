package com.franco.dev.service.operaciones;

import com.franco.dev.service.ia.FacturaIaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FacturaSifenXmlParserServiceTest {

    private FacturaSifenXmlParserService service;
    private String xmlFixture;

    @BeforeEach
    void setUp() throws IOException {
        service = new FacturaSifenXmlParserService();
        xmlFixture = new String(
                Files.readAllBytes(Paths.get("src/test/resources/fixtures/facturas/sifen-de-aprobado.xml")),
                StandardCharsets.UTF_8);
    }

    @Test
    void parsearXml_fixtureReal_extraeCabeceraCompleta() {
        FacturaIaResponse r = service.parsearXml(xmlFixture);

        assertNull(r.getError(), "fixture es DE valido, no debe tener error");
        assertEquals(Boolean.TRUE, r.getEsLegal(), "DE SIFEN es factura legal");
        assertEquals("80099482-5", r.getEmisorRuc(), "ruc + dv concatenados");
        assertEquals("FRANCO AREVALOS S.A.", r.getEmisorNombre());
        assertEquals("001-002-0000038", r.getNumeroFactura());
        assertEquals("18270044", r.getTimbrado());
        assertEquals("2025-12-04", r.getFechaEmision(), "solo fecha sin hora");
        assertEquals("PYG", r.getMoneda());
        assertEquals(0, new BigDecimal("96000").compareTo(r.getTotalGeneral()));
    }

    @Test
    void parsearXml_fixtureReal_extraeDatosAdicionalesDelEmisor() {
        FacturaIaResponse r = service.parsearXml(xmlFixture);

        // Datos del emisor para precargar/actualizar el proveedor (fase B)
        assertEquals("Av Paraguay casi 30 de Julio", r.getEmisorDireccion());
        assertEquals("SALTO DEL GUAIRA", r.getEmisorCiudad());
        assertEquals("0986128000", r.getEmisorTelefono());
        assertEquals("francoarevalos05@gmail.com", r.getEmisorEmail());
    }

    @Test
    void parsearXml_fixtureReal_extraeItems() {
        FacturaIaResponse r = service.parsearXml(xmlFixture);

        assertNotNull(r.getItems());
        assertEquals(1, r.getItems().size());
        FacturaIaResponse.Item item = r.getItems().get(0);

        assertEquals("001", item.getCodigoProducto());
        assertEquals("CERVEZA DON FRACO LAGER 330 ML", item.getNombreProducto());
        assertEquals(0, new BigDecimal("12").compareTo(item.getCantidad()));
        assertEquals(0, new BigDecimal("8000.00").compareTo(item.getPrecioUnitario()));
        assertEquals(0, new BigDecimal("0").compareTo(item.getDescuento()));
        assertEquals(0, new BigDecimal("96000").compareTo(item.getTotalItem()));
    }

    @Test
    void parsearXml_null_retornaError() {
        FacturaIaResponse r = service.parsearXml(null);
        assertEquals("XML_VACIO", r.getError());
    }

    @Test
    void parsearXml_vacio_retornaError() {
        FacturaIaResponse r = service.parsearXml("   ");
        assertEquals("XML_VACIO", r.getError());
    }

    @Test
    void parsearXml_noEsSifen_retornaError() {
        String xmlNoSifen = "<?xml version=\"1.0\"?><Otro><dato>hola</dato></Otro>";
        FacturaIaResponse r = service.parsearXml(xmlNoSifen);
        assertEquals("NO_ES_DE_SIFEN", r.getError());
    }

    @Test
    void parsearXml_sinTimbrado_funcionaPeroSinTimbrado() {
        // XML mock minimo con rDE + emisor + 1 item, sin timbrado real
        String xmlMinimo = "<rDE><DE><gTimb></gTimb>" +
                "<gDatGralOpe><gOpeCom><cMoneOpe>PYG</cMoneOpe></gOpeCom>" +
                "<gEmis><dRucEm>12345</dRucEm><dDVEmi>6</dDVEmi><dNomEmi>TEST SA</dNomEmi></gEmis>" +
                "</gDatGralOpe>" +
                "<gDtipDE><gCamItem>" +
                "<dCodInt>X1</dCodInt><dDesProSer>ITEM TEST</dDesProSer>" +
                "<dCantProSer>5</dCantProSer><gValorItem><dPUniProSer>1000</dPUniProSer>" +
                "<gValorRestaItem><dDescItem>0</dDescItem><dTotOpeItem>5000</dTotOpeItem>" +
                "</gValorRestaItem></gValorItem></gCamItem></gDtipDE>" +
                "<gTotSub><dTotGralOpe>5000</dTotGralOpe></gTotSub></DE></rDE>";

        FacturaIaResponse r = service.parsearXml(xmlMinimo);

        assertNull(r.getError());
        assertEquals("12345-6", r.getEmisorRuc());
        assertEquals("TEST SA", r.getEmisorNombre());
        assertEquals(1, r.getItems().size());
        assertEquals("ITEM TEST", r.getItems().get(0).getNombreProducto());
        assertEquals(0, new BigDecimal("5000").compareTo(r.getTotalGeneral()));
    }

    @Test
    void parsearXml_multipleItems_extraeTodos() {
        String xmlConItems = "<rDE><DE>" +
                "<gEmis><dRucEm>123</dRucEm></gEmis>" +
                "<gCamItem><dCodInt>A1</dCodInt><dDesProSer>ITEM A</dDesProSer><dCantProSer>1</dCantProSer></gCamItem>" +
                "<gCamItem><dCodInt>B2</dCodInt><dDesProSer>ITEM B</dDesProSer><dCantProSer>2</dCantProSer></gCamItem>" +
                "<gCamItem><dCodInt>C3</dCodInt><dDesProSer>ITEM C</dDesProSer><dCantProSer>3</dCantProSer></gCamItem>" +
                "</DE></rDE>";

        FacturaIaResponse r = service.parsearXml(xmlConItems);

        assertNotNull(r.getItems());
        assertEquals(3, r.getItems().size());
        assertEquals("A1", r.getItems().get(0).getCodigoProducto());
        assertEquals("B2", r.getItems().get(1).getCodigoProducto());
        assertEquals("C3", r.getItems().get(2).getCodigoProducto());
    }

    @Test
    void extractAllFullTags_helperIteraTodasOcurrencias() {
        String xml = "<x>1</x><otro/><x>2</x><x>3</x>";
        List<String> tags = service.extractAllFullTags(xml, "x");
        assertEquals(3, tags.size());
        assertEquals("<x>1</x>", tags.get(0));
        assertEquals("<x>2</x>", tags.get(1));
        assertEquals("<x>3</x>", tags.get(2));
    }

    @Test
    void parsearXml_fechaSinHora_funcionaPeroPuedeQuedarComoEsta() {
        String xml = "<rDE><DE><gDatGralOpe><dFeEmiDE>2026-01-15</dFeEmiDE></gDatGralOpe>" +
                "<gEmis><dRucEm>1</dRucEm></gEmis></DE></rDE>";
        FacturaIaResponse r = service.parsearXml(xml);
        assertEquals("2026-01-15", r.getFechaEmision());
    }
}
