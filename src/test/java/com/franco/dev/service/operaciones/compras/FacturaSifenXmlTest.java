package com.franco.dev.service.operaciones.compras;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FacturaSifenXmlTest {

    /**
     * Respuesta de siConsDE armada con la estructura real de SIFEN (rDE dentro de xContenDE, namespaces con
     * prefijo) y dos renglones de la factura de MARVI: el cobrado y el bonificado al 100 %.
     */
    static String respuestaMarvi(boolean escapada) {
        String rde = "<rDE xmlns=\"http://ekuatia.set.gov.py/sifen/xsd\"><dVerFor>150</dVerFor>"
                + "<DE Id=\"" + KudeQrTest.CDC_MARVI + "\"><dDVId>1</dDVId>"
                + "<gTimb><iTiDE>1</iTiDE><dDesTiDE>Factura electrónica</dDesTiDE><dNumTim>18153001</dNumTim>"
                + "<dEst>001</dEst><dPunExp>001</dPunExp><dNumDoc>0067495</dNumDoc></gTimb>"
                + "<gDatGralOpe><dFeEmiDE>2026-10-08T13:30:00</dFeEmiDE>"
                + "<gOpeCom><iTipTra>1</iTipTra><cMoneOpe>PYG</cMoneOpe><dDesMoneOpe>Guarani</dDesMoneOpe></gOpeCom>"
                + "<gEmis><dRucEm>80062507</dRucEm><dDVEmi>2</dDVEmi><dNomEmi>MARVI S.R.L.</dNomEmi></gEmis>"
                + "<gDatRec><dRucRec>80099482</dRucRec><dDVRec>5</dDVRec><dNomRec>FRANCO AREVALOS S.A</dNomRec></gDatRec>"
                + "</gDatGralOpe><gDtipDE><gCamFE><iIndPres>1</iIndPres></gCamFE>"
                + "<gCamCond><iCondOpe>2</iCondOpe><dDCondOpe>Crédito</dDCondOpe></gCamCond>"
                + item("3002169", "07841503002169", "SENSEI PAPAS FRITAS ORIGINAL 140gr.C/20", "80", "15651", "0", "0.00", "1252080")
                + item("3002169", "07841503002169", "SENSEI PAPAS FRITAS ORIGINAL 140gr.C/20", "8", "15651", "15651", "100.00", "0")
                + "</gDtipDE><gTotSub><dSubExe>0</dSubExe><dSub5>0</dSub5><dSub10>1252080</dSub10>"
                + "<dTotDesc>125208</dTotDesc><dTotGralOpe>1252080</dTotGralOpe><dIVA5>0</dIVA5><dIVA10>113825</dIVA10>"
                + "<dTotIVA>113825</dTotIVA></gTotSub></DE></rDE>";
        String contenido = escapada ? rde.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") : rde;
        return "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\"><env:Body>"
                + "<ns2:rEnviConsDeResponse xmlns:ns2=\"http://ekuatia.set.gov.py/sifen/xsd\">"
                + "<ns2:dFecProc>2026-10-21T11:40:00</ns2:dFecProc><ns2:dCodRes>0422</ns2:dCodRes>"
                + "<ns2:dMsgRes>CDC encontrado</ns2:dMsgRes><ns2:xContenDE>" + contenido + "</ns2:xContenDE>"
                + "</ns2:rEnviConsDeResponse></env:Body></env:Envelope>";
    }

    private static String item(String codInt, String gtin, String desc, String cant, String precio, String desc1,
                               String porc, String total) {
        return "<gCamItem><dCodInt>" + codInt + "</dCodInt><dGtin>" + gtin + "</dGtin><dDesProSer>" + desc
                + "</dDesProSer><cUniMed>77</cUniMed><dDesUniMed>UNI</dDesUniMed><dCantProSer>" + cant + "</dCantProSer>"
                + "<gValorItem><dPUniProSer>" + precio + "</dPUniProSer><dTotBruOpeItem>0</dTotBruOpeItem>"
                + "<gValorRestaItem><dDescItem>" + desc1 + "</dDescItem><dPorcDesIt>" + porc + "</dPorcDesIt>"
                + "<dTotOpeItem>" + total + "</dTotOpeItem></gValorRestaItem></gValorItem>"
                + "<gCamIVA><iAfecIVA>1</iAfecIVA><dTasaIVA>10</dTasaIVA></gCamIVA>"
                + "<gRasMerc><dNumLote>L2610</dNumLote><dVencMerc>2027-03-31</dVencMerc></gRasMerc></gCamItem>";
    }

    @Test
    void leeCabeceraTotalesYRenglones() {
        FacturaSifenXml.Cabecera c = FacturaSifenXml.leer(respuestaMarvi(false));
        assertEquals(KudeQrTest.CDC_MARVI, c.cdc);
        assertEquals(1, c.tipoDocumento);
        assertEquals(18153001, c.timbrado);
        assertEquals("001", c.establecimiento);
        assertEquals("0067495", c.numero);
        assertEquals(LocalDateTime.of(2026, 10, 8, 13, 30), c.fechaEmision);
        assertEquals("80062507", c.rucEmisor);
        assertEquals("MARVI S.R.L.", c.razonSocialEmisor);
        assertEquals("80099482", c.rucReceptor);
        assertEquals("PYG", c.moneda);
        assertEquals(2, c.condicionOperacion);
        assertEquals(1252080.0, c.totalGeneral);
        assertEquals(113825.0, c.totalIva10);
        assertFalse(c.cancelada);
        assertEquals(2, c.renglones.size());
    }

    @Test
    void elCodigoDeBarrasConservaLosCerosALaIzquierda() {
        // jsifenlib lo tipa como long y lo perdería: por eso el parser es propio
        FacturaSifenXml.Renglon r = FacturaSifenXml.leer(respuestaMarvi(false)).renglones.get(0);
        assertEquals("07841503002169", r.gtin);
    }

    @Test
    void elRenglonBonificadoTraeElDescuentoDelCienPorCiento() {
        FacturaSifenXml.Renglon cobrado = FacturaSifenXml.leer(respuestaMarvi(false)).renglones.get(0);
        FacturaSifenXml.Renglon bonif = FacturaSifenXml.leer(respuestaMarvi(false)).renglones.get(1);
        assertEquals(80.0, cobrado.cantidad);
        assertEquals(15651.0, cobrado.precioUnitario);
        assertEquals(0.0, cobrado.descuentoUnitario);
        assertEquals(8.0, bonif.cantidad);
        assertEquals(100.0, bonif.porcentajeDescuento);
        assertEquals(15651.0, bonif.descuentoUnitario);
        assertEquals(10, bonif.tasaIva);
        assertEquals("L2610", bonif.lote);
        assertEquals(LocalDate.of(2027, 3, 31), bonif.vencimiento);
        assertEquals("UNI", bonif.unidadMedida);
        assertEquals(2, bonif.orden);
    }

    @Test
    void elRdeEscapadoComoTextoTambienSeLee() {
        FacturaSifenXml.Cabecera c = FacturaSifenXml.leer(respuestaMarvi(true));
        assertEquals("80062507", c.rucEmisor);
        assertEquals(2, c.renglones.size());
    }

    @Test
    void unEventoDeCancelacionMarcaLaFactura() {
        String conEvento = respuestaMarvi(false).replace("</ns2:rEnviConsDeResponse>",
                "<ns2:xContEv><rGeVeCan><mOtEve>Error</mOtEve></rGeVeCan></ns2:xContEv></ns2:rEnviConsDeResponse>");
        assertTrue(FacturaSifenXml.leer(conEvento).cancelada);
    }

    @Test
    void unaRespuestaSinFacturaSeRechazaConMotivo() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FacturaSifenXml.leer(
                "<env:Envelope xmlns:env=\"x\"><env:Body><dCodRes>0420</dCodRes></env:Body></env:Envelope>"));
        assertTrue(e.getMessage().contains("rDE"));
    }

    @Test
    void unDoctypeNoSeProcesa() {
        assertThrows(IllegalArgumentException.class, () -> FacturaSifenXml.leer(
                "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"));
    }
}
