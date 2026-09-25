package com.franco.dev.service.sifen;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.ModalidadTransporteNr;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.financiero.enums.ResponsableEmisionNr;
import com.franco.dev.domain.financiero.enums.TipoTransporteNr;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Se asserta sobre el bean de jsifenlib, antes de firmar: no hace falta certificado ni red, y es
 * donde viven las reglas que SIFEN rechaza.
 */
class SifenNotaRemisionBuilderTest {

    private SifenNotaRemisionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SifenNotaRemisionBuilder();
        builder.configurar(2, true, "Documento emitido como Nota de Remision Electronica conforme RG 41/2014.");
    }

    @Test
    void esUnaNotaDeRemisionConMensajeFiscalYSinTotales() {
        DocumentoElectronico de = construir(notaManual(), items());

        assertEquals(TTiDE.NOTA_DE_REMISION_ELECTRONICA, de.getgTimb().getiTiDE());
        assertNotNull(de.getgOpeDE().getdInfoFisc(), "dInfoFisc es obligatorio en una NRE");
        assertNull(de.getgTotSub(), "una NRE no lleva totales");
        assertEquals(CMondT.PYG, de.getgDatGralOpe().getgOpeCom().getcMoneOpe());
    }

    @Test
    void elKilometrajeSiempreViaja_aunqueLaNotaNoLoTenga() {
        // SIFEN de produccion rechazo el lote entero el 2026-09-18 con
        // «0160 XML malformado: [Elemento esperado: dKmR dentro de: gCamNRE]»: dKmR es
        // obligatorio siempre, no solo cuando hay fecha estimada de factura.
        NotaRemision nota = notaManual();
        nota.setKmEstimado(null);

        DocumentoElectronico de = construir(nota, items());

        assertEquals(1, de.getgDtipDE().getgCamNRE().getdKmR(), "dKmR es obligatorio en toda NRE");
    }

    @Test
    void elKilometrajeCargadoSeRespeta() {
        NotaRemision nota = notaManual();
        nota.setKmEstimado(143);

        DocumentoElectronico de = construir(nota, items());

        assertEquals(143, de.getgDtipDE().getgCamNRE().getdKmR());
    }

    @Test
    void losItemsViajanSinPrecioNiIva() {
        DocumentoElectronico de = construir(notaManual(), items());

        assertEquals(1, de.getgDtipDE().getgCamItemList().size());
        assertNull(de.getgDtipDE().getgCamItemList().get(0).getgValorItem());
        assertNull(de.getgDtipDE().getgCamItemList().get(0).getgCamIVA());
        assertEquals(new BigDecimal("3"), de.getgDtipDE().getgCamItemList().get(0).getdCantProSer());
    }

    @Test
    void elMotivoSeMapeaPorNombreDeEnum() {
        // El bug de la referencia: persistía "1".."14" y valueOf caía siempre en TRASLADO_POR_VENTAS.
        NotaRemision nota = notaManual();
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_ENTRE_LOCALES);

        DocumentoElectronico de = construir(nota, items());

        assertEquals(TiMotivTras.TRASLADO_ENTRE_LOCALES, de.getgDtipDE().getgCamNRE().getiMotEmiNR());
    }

    @Test
    void elEstablecimientoSaleDeLaSucursalYElNumeroTieneSieteDigitos() {
        Sucursal sucursal = new Sucursal();
        sucursal.setCodigoEstablecimientoFactura("2");
        NotaRemision nota = notaManual();
        nota.setNumeroNotaRemision(42);

        DocumentoElectronico de = builder.construir(nota, items(), timbrado(), sucursal, null);

        assertEquals("002", de.getgTimb().getdEst());
        assertEquals("0000042", de.getgTimb().getdNumDoc());
        assertEquals("001", de.getgTimb().getdPunExp());
    }

    @Test
    void laMarcaDelVehiculoSeAbreviaAdiezCaracteres() {
        NotaRemision nota = notaManual();
        nota.setVehiculoMarca("Mercedes Benz");

        DocumentoElectronico de = construir(nota, items());

        String marca = de.getgDtipDE().getgTransp().getgVehTrasList().get(0).getdMarVeh();
        assertEquals("MERCEDES", marca);
        assertTrue(marca.length() <= 10);
    }

    @Test
    void elResponsableDelFleteSaleDelTipoDeTransporte() {
        NotaRemision propio = notaManual();
        propio.setTipoTransporte(TipoTransporteNr.PROPIO);
        assertEquals(TiRespFlete.TRANSPORTE_PROPIO,
                construir(propio, items()).getgDtipDE().getgTransp().getiRespFlete());

        NotaRemision tercero = notaManual();
        tercero.setTipoTransporte(TipoTransporteNr.TERCERO);
        assertEquals(TiRespFlete.TERCERO,
                construir(tercero, items()).getgDtipDE().getgTransp().getiRespFlete());
    }

    @Test
    void elChoferEnTransportePropioDependeDeLaProperty() {
        NotaRemision nota = notaManual();
        nota.setTipoTransporte(TipoTransporteNr.PROPIO);

        assertEquals("JUAN PEREZ",
                construir(nota, items()).getgDtipDE().getgTransp().getgCamTrans().getdNomChof());

        builder.configurar(2, false, "mensaje fiscal");
        assertNull(construir(nota, items()).getgDtipDE().getgTransp().getgCamTrans().getdNomChof(),
                "con la property apagada el chofer no se informa en transporte propio");
    }

    @Test
    void elChoferEnTransporteDeTercerosSeInformaSiempre() {
        builder.configurar(2, false, "mensaje fiscal");
        NotaRemision nota = notaManual();
        nota.setTipoTransporte(TipoTransporteNr.TERCERO);

        assertEquals("JUAN PEREZ",
                construir(nota, items()).getgDtipDE().getgTransp().getgCamTrans().getdNomChof());
    }

    @Test
    void elDocumentoAsociadoSoloVaSiHayFacturaConCdc() {
        assertNull(construir(notaManual(), items()).getgCamDEAsocList());

        DocumentoElectronico conFactura = builder.construir(notaManual(), items(), timbrado(), sucursal(),
                "01800695631001001000000612021112917595714694");
        assertEquals(1, conFactura.getgCamDEAsocList().size());
        assertEquals(TiTipDocAso.ELECTRONICO, conFactura.getgCamDEAsocList().get(0).getiTipDocAso());
    }

    @Test
    void laFechaEstimadaDeFacturaSeAcotaACincoDias() {
        NotaRemision nota = notaManual();
        LocalDateTime emision = nota.getFecha();
        nota.setFechaEstimadaFactura(emision.toLocalDate().plusDays(30));

        DocumentoElectronico de = construir(nota, items());

        assertEquals(emision.toLocalDate().plusDays(5), de.getgDtipDE().getgCamNRE().getdFecEm());
        assertTrue(de.getgDtipDE().getgCamNRE().getdKmR() >= 1,
                "el XSD exige dKmR cuando va dFecEm");
    }

    @Test
    void elReceptorConRucEsContribuyenteYNuncaEsInnominado() {
        DocumentoElectronico de = construir(notaManual(), items());

        assertEquals(TiNatRec.CONTRIBUYENTE, de.getgDatGralOpe().getgDatRec().getiNatRec());
        assertEquals("80012345", de.getgDatGralOpe().getgDatRec().getdRucRec());
        assertNotEquals(TiTipDocRec.INNOMINADO, de.getgDatGralOpe().getgDatRec().getiTipIDRec());
        assertNotNull(de.getgDatGralOpe().getgDatRec().getdDirRec());
    }

    @Test
    void elReceptorConCedulaEsNoContribuyentePeroIdentificado() {
        NotaRemision nota = notaManual();
        nota.setReceptorRuc("4567890");

        DocumentoElectronico de = construir(nota, items());

        assertEquals(TiNatRec.NO_CONTRIBUYENTE, de.getgDatGralOpe().getgDatRec().getiNatRec());
        assertEquals(TiTipDocRec.CEDULA_PARAGUAYA, de.getgDatGralOpe().getgDatRec().getiTipIDRec());
        assertEquals("4567890", de.getgDatGralOpe().getgDatRec().getdNumIDRec());
    }

    @Test
    void elTransporteLlevaSalidaEntregaVehiculoYTransportista() {
        DocumentoElectronico de = construir(notaManual(), items());
        var transporte = de.getgDtipDE().getgTransp();

        assertEquals(TiTTrans.PROPIO, transporte.getiTipTrans());
        assertEquals(TiModTrans.TERRESTRE, transporte.getiModTrans());
        assertEquals(1, transporte.getgCamSal().getcCiuSal());
        assertEquals(12, transporte.getgCamEntList().get(0).getcCiuEnt());
        assertEquals(PaisType.PRY, transporte.getcPaisDest());
        assertNotNull(transporte.getgCamTrans());
        assertNull(transporte.getcCondNeg(), "cCondNeg se omite a propósito");
    }

    @Test
    void elTelefonoDelEmisorSaleDelDetalle() {
        assertEquals("021123456", construir(notaManual(), items()).getgDatGralOpe().getgEmis().getdTelEmi());
    }

    @Test
    void sinTelefonoEnElDetalleUsaElDeLaCabecera() {
        // SIFEN rechazo el 2026-09-23 con «0160 XML malformado: [El valor del elemento: dTelEmi es
        // invalido]»: el detalle del deposito no tenia telefono y la cabecera del timbrado si.
        TimbradoDetalle detalle = timbrado();
        detalle.setTelefono("  ");
        detalle.getTimbrado().setTelefono("0982700027");

        DocumentoElectronico de = builder.construir(notaManual(), items(), detalle, sucursal(), null);

        assertEquals("0982700027", de.getgDatGralOpe().getgEmis().getdTelEmi());
    }

    private DocumentoElectronico construir(NotaRemision nota, List<NotaRemisionItem> items) {
        return builder.construir(nota, items, timbrado(), sucursal(), null);
    }

    private static Sucursal sucursal() {
        Sucursal sucursal = new Sucursal();
        sucursal.setCodigoEstablecimientoFactura("1");
        return sucursal;
    }

    private static TimbradoDetalle timbrado() {
        Timbrado timbrado = new Timbrado();
        timbrado.setNumero("12345678");
        timbrado.setRuc("80012345-6");
        timbrado.setRazonSocial("FRANCO SA");
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
        detalle.setDepartamento("CENTRAL");
        detalle.setCodigoCiudad("1");
        detalle.setCiudad("ASUNCION");
        detalle.setActivo(true);
        return detalle;
    }

    private static NotaRemision notaManual() {
        NotaRemision nota = new NotaRemision();
        nota.setId(1L);
        nota.setSucursalId(1L);
        nota.setTimbradoDetalleId(10L);
        nota.setNumeroNotaRemision(1);
        nota.setFecha(LocalDateTime.now().minusMinutes(5));
        nota.setOrigen(OrigenNotaRemision.MANUAL);
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_POR_CONSIGNACION);
        nota.setResponsableEmision(ResponsableEmisionNr.EMISOR_FACTURA);
        nota.setReceptorNombre("CLIENTE SA");
        nota.setReceptorRuc("80012345-6");
        nota.setReceptorDireccion("RUTA 1 KM 20");
        nota.setReceptorDepartamento("CENTRAL");
        nota.setReceptorCodigoCiudad(12);
        nota.setReceptorCiudad("SAN LORENZO");
        nota.setSalidaDireccion("DEPOSITO CENTRAL");
        nota.setSalidaDepartamento("CAPITAL");
        nota.setSalidaCodigoCiudad(1);
        nota.setSalidaCiudad("ASUNCION");
        nota.setEntregaDireccion("RUTA 1 KM 20");
        nota.setEntregaDepartamento("CENTRAL");
        nota.setEntregaCodigoCiudad(12);
        nota.setEntregaCiudad("SAN LORENZO");
        nota.setTipoTransporte(TipoTransporteNr.PROPIO);
        nota.setModalidadTransporte(ModalidadTransporteNr.TERRESTRE);
        nota.setVehiculoMarca("TOYOTA");
        nota.setVehiculoMatricula("ABC123");
        nota.setChoferNombre("JUAN PEREZ");
        nota.setChoferDocumento("1234567");
        nota.setChoferDireccion("BARRIO SAN VICENTE");
        nota.setFechaInicioTraslado(LocalDate.now());
        nota.setActivo(true);
        return nota;
    }

    private static List<NotaRemisionItem> items() {
        NotaRemisionItem item = new NotaRemisionItem();
        item.setDescripcion("CAJA DE GASEOSAS");
        item.setCantidad(new BigDecimal("3"));
        item.setUnidadMedida("UNI");
        return Collections.singletonList(item);
    }
}
