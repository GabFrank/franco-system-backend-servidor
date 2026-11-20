package com.franco.dev.service.sifen;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.domain.financiero.NotaCreditoItem;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaCredito;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.types.*;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Se asserta sobre el bean, antes de firmar: ahí viven las reglas fiscales de la NC. */
class SifenNotaCreditoBuilderTest {

    private static final String CDC_FACTURA = "01800123456001001000000122026091712345678901";

    private SifenNotaCreditoBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SifenNotaCreditoBuilder();
        builder.configurar(2);
    }

    @Test
    void esUnaNotaDeCreditoConTotalesYDocumentoAsociado() {
        DocumentoElectronico de = construir(nota(null, null));

        assertEquals(TTiDE.NOTA_DE_CREDITO_ELECTRONICA, de.getgTimb().getiTiDE());
        assertNotNull(de.getgTotSub(), "una NC SI lleva totales");
        assertEquals(1, de.getgCamDEAsocList().size());
        assertEquals(CDC_FACTURA, de.getgCamDEAsocList().get(0).getdCdCDERef());
        assertEquals(TiTipDocAso.ELECTRONICO, de.getgCamDEAsocList().get(0).getiTipDocAso());
    }

    @Test
    void elMotivoSeMapeaPorNombreDeEnum() {
        NotaCredito nota = nota(null, null);
        nota.setMotivoEmision(MotivoEmisionNotaCredito.BONIFICACION);

        assertEquals(TiMotEmi.BONIFICACION, construir(nota).getgDtipDE().getgCamNCDE().getiMotEmi());
    }

    @Test
    void enGuaraniesNoSeInformaTipoDeCambio() {
        DocumentoElectronico de = construir(nota(null, null));

        assertEquals(CMondT.PYG, de.getgDatGralOpe().getgOpeCom().getcMoneOpe());
        assertNull(de.getgDatGralOpe().getgOpeCom().getdTiCam(),
                "en PYG no van dTiCam ni dCondTiCam");
    }

    @Test
    void enMonedaExtranjeraViajaElCambioConSeisDecimales() {
        DocumentoElectronico de = construir(nota("USD", new BigDecimal("7300")));

        assertEquals(CMondT.USD, de.getgDatGralOpe().getgOpeCom().getcMoneOpe());
        assertEquals(TdCondTiCam.GLOBAL, de.getgDatGralOpe().getgOpeCom().getdCondTiCam());
        assertEquals(6, de.getgDatGralOpe().getgOpeCom().getdTiCam().scale());
    }

    @Test
    void sinTipoDeCambioEnMonedaExtranjeraFalla() {
        assertThrows(IllegalArgumentException.class, () -> construir(nota("USD", null)));
    }

    @Test
    void laCondicionEsSiempreContadoConEntregaEnEfectivo() {
        DocumentoElectronico de = construir(nota(null, null));

        assertEquals(TiCondOpe.CONTADO, de.getgDtipDE().getgCamCond().getiCondOpe());
        assertEquals(1, de.getgDtipDE().getgCamCond().getgPaConEIniList().size());
        assertEquals(TiTiPago.EFECTIVO,
                de.getgDtipDE().getgCamCond().getgPaConEIniList().get(0).getiTiPago());
        assertNull(de.getgDtipDE().getgCamCond().getgPagCred(), "una NC no se financia a credito");
    }

    @Test
    void losItemsLlevanPrecioEIva() {
        DocumentoElectronico de = construir(nota(null, null));

        assertEquals(1, de.getgDtipDE().getgCamItemList().size());
        assertNotNull(de.getgDtipDE().getgCamItemList().get(0).getgValorItem());
        assertNotNull(de.getgDtipDE().getgCamItemList().get(0).getgCamIVA());
    }

    @Test
    void enMonedaExtranjeraElPrecioSeConvierte() {
        DocumentoElectronico de = construir(nota("USD", new BigDecimal("7300")));

        BigDecimal precio = de.getgDtipDE().getgCamItemList().get(0).getgValorItem().getdPUniProSer();
        assertEquals(0, new BigDecimal("15.0685").compareTo(precio),
                "110.000 Gs / 7.300 = 15,0685 USD");
    }

    @Test
    void sinTelefonoEnElDetalleUsaElDeLaCabecera() {
        TimbradoDetalle detalle = timbrado();
        detalle.setTelefono(null);
        detalle.getTimbrado().setTelefono("0982700027");

        DocumentoElectronico de = builder.construir(nota("PYG", null), items(), detalle, sucursal(), CDC_FACTURA);

        assertEquals("0982700027", de.getgDatGralOpe().getgEmis().getdTelEmi());
    }

    @Test
    void sinTelefonoEnNingunLadoElValidadorCortaAntesDeEnviar() {
        TimbradoDetalle detalle = timbrado();
        detalle.setTelefono("");

        DocumentoElectronico de = builder.construir(nota("PYG", null), items(), detalle, sucursal(), CDC_FACTURA);

        GraphQLException e = assertThrows(GraphQLException.class, () -> SifenNotasValidator.validarNCE(de));
        assertTrue(e.getMessage().contains("teléfono"), "mensaje inesperado: " + e.getMessage());
    }

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path dir;

    /** #666: la NC de una factura B2F lleva el mismo receptor del exterior, y el XML se genera y firma. */
    @Test
    void conElReceptorB2FDeLaFacturaElXmlSaleB2F() throws Exception {
        com.roshka.sifen.core.fields.request.de.TgDatRec receptor =
                com.franco.dev.service.sifen.util.SifenReceptorDesdeXml.receptorB2F(ReceptorExtranjeroCentralTest.xmlFactura(4));
        NotaCredito nota = nota(null, null);
        nota.setNombre("JOAO DA SILVA");
        nota.setRuc("FX987654");
        DocumentoElectronico de = builder.construir(nota, items(), timbrado(), sucursal(), CDC_FACTURA, receptor);

        com.roshka.sifen.core.SifenConfig config = new com.roshka.sifen.core.SifenConfig();
        config.setUsarCertificadoCliente(true);
        config.setTipoCertificadoCliente(com.roshka.sifen.core.SifenConfig.TipoCertificadoCliente.PFX);
        config.setCertificadoCliente(certificadoDescartable());
        config.setContrasenaCertificadoCliente("prueba");
        config.setHabilitarNotaTecnica13(true);
        String xml = de.generarXml(com.roshka.sifen.internal.ctx.GenerationCtx.getDefaultFromConfig(config));
        String gDatRec = xml.substring(xml.indexOf("<gDatRec>"), xml.indexOf("</gDatRec>"));
        assertTrue(gDatRec.contains("<iTiOpe>4</iTiOpe>"), gDatRec);
        assertTrue(gDatRec.contains("<cPaisRec>BRA</cPaisRec>"), gDatRec);
        assertTrue(gDatRec.contains("<iTipIDRec>" + TiTipDocRec.PASAPORTE.getVal() + "</iTipIDRec>"), gDatRec);
        assertTrue(gDatRec.contains("<dNumIDRec>FX987654</dNumIDRec>"), gDatRec);
        assertTrue(gDatRec.contains("<dDirRec>RUA 1</dDirRec>"), gDatRec);
        assertFalse(gDatRec.contains("dRucRec"), gDatRec);
        assertTrue(xml.contains("SignatureValue"));
        // ida y vuelta: el lector entiende el XML que genera la libreria (no solo uno escrito a mano)
        com.roshka.sifen.core.fields.request.de.TgDatRec leido =
                com.franco.dev.service.sifen.util.SifenReceptorDesdeXml.receptorB2F(xml);
        assertNotNull(leido, "el XML real de la libreria tiene que leerse");
        assertEquals(PaisType.BRA, leido.getcPaisRec());
        assertEquals(TiTipDocRec.PASAPORTE, leido.getiTipIDRec());
    }

    /** Autofirmado, solo para firmar en el test: lo crea keytool del JDK que corre los tests. */
    private String certificadoDescartable() throws Exception {
        java.io.File pfx = dir.resolve("prueba.pfx").toFile();
        String keytool = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "prueba", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-dname", "CN=prueba", "-storetype", "PKCS12",
                "-keystore", pfx.getPath(), "-storepass", "prueba", "-keypass", "prueba")
                .redirectErrorStream(true).start();
        assertEquals(0, p.waitFor(), "keytool");
        return pfx.getPath();
    }

    private DocumentoElectronico construir(NotaCredito nota) {
        return builder.construir(nota, items(), timbrado(), sucursal(), CDC_FACTURA);
    }

    private static NotaCredito nota(String moneda, BigDecimal cambio) {
        NotaCredito nota = new NotaCredito();
        nota.setId(1L);
        nota.setSucursalId(1L);
        nota.setTimbradoDetalleId(10L);
        nota.setNumeroNotaCredito(1);
        nota.setFecha(LocalDateTime.now().minusMinutes(2));
        nota.setFacturaLegalId(300L);
        nota.setMotivoEmision(MotivoEmisionNotaCredito.DEVOLUCION);
        nota.setNombre("CLIENTE SA");
        nota.setRuc("80012345-6");
        nota.setDireccion("AVDA ESPAÑA 123");
        nota.setMonedaExtranjera(moneda);
        nota.setTipoCambio(cambio);
        nota.setTotalFinal(new BigDecimal("110000"));
        nota.setActivo(true);
        return nota;
    }

    private static List<NotaCreditoItem> items() {
        NotaCreditoItem item = new NotaCreditoItem();
        item.setDescripcion("PRODUCTO DE PRUEBA");
        item.setCantidad(BigDecimal.ONE);
        item.setPrecioUnitario(new BigDecimal("110000"));
        item.setTotal(new BigDecimal("110000"));
        item.setIva(10);
        return Collections.singletonList(item);
    }

    private static Sucursal sucursal() {
        Sucursal sucursal = new Sucursal();
        sucursal.setCodigoEstablecimientoFactura("1");
        return sucursal;
    }

    private static TimbradoDetalle timbrado() {
        Timbrado timbrado = new Timbrado();
        timbrado.setNumero("12345678");
        timbrado.setRuc("80099482-5");
        timbrado.setRazonSocial("FRANCO AREVALOS S.A.");
        timbrado.setEmail("facturacion@franco.com.py");
        timbrado.setFechaInicio(LocalDateTime.now().minusMonths(6));
        timbrado.setCodActividadEconomicaPrincipal("47111");
        timbrado.setDescActividadEconomicaPrincipal("VENTA AL POR MENOR");

        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setId(10L);
        detalle.setSucursalId(1L);
        detalle.setTimbrado(timbrado);
        detalle.setPuntoExpedicion("1");
        detalle.setDireccion("AVDA ESPAÑA 123");
        detalle.setTelefono("021123456");
        detalle.setDepartamento("CANINDEYU");
        detalle.setCodigoCiudad("4738");
        detalle.setCiudad("SALTO DEL GUAIRA");
        detalle.setActivo(true);
        return detalle;
    }
}
