package com.franco.dev.service.sifen;

import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/** Cada caso es un rechazo real de SIFEN que se atrapa antes de consumir el número. */
class SifenNotasValidatorTest {

    @Test
    void unDeCompletoPasa() {
        SifenNotasValidator.validarNRE(deValido());
    }

    @Test
    void sinMensajeFiscalFalla() {
        DocumentoElectronico de = deValido();
        de.getgOpeDE().setdInfoFisc(null);

        assertMensaje(de, "mensaje fiscal");
    }

    @Test
    void conTotalesFalla() {
        DocumentoElectronico de = deValido();
        de.setgTotSub(new TgTotSub());

        assertMensaje(de, "no lleva totales");
    }

    @Test
    void conPrecioEnUnItemFalla() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgCamItemList().get(0).setgValorItem(new TgValorItem());

        assertMensaje(de, "no llevan precio");
    }

    @Test
    void conReceptorInnominadoFalla() {
        DocumentoElectronico de = deValido();
        de.getgDatGralOpe().getgDatRec().setiTipIDRec(TiTipDocRec.INNOMINADO);

        assertMensaje(de, "innominado");
    }

    @Test
    void sinCiudadDeSalidaFalla() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgTransp().getgCamSal().setcCiuSal(0);

        assertMensaje(de, "ciudad de salida");
    }

    @Test
    void sinCiudadDeEntregaFalla() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgTransp().getgCamEntList().get(0).setcCiuEnt(0);

        assertMensaje(de, "ciudad de entrega");
    }

    @Test
    void trasladoPorVentasSinFacturaNiFechaEstimadaFalla() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgCamNRE().setiMotEmiNR(TiMotivTras.TRASLADO_POR_VENTAS);
        de.getgDtipDE().getgCamNRE().setdFecEm(null);   // sin fecha estimada y sin factura asociada

        assertMensaje(de, "fecha");
    }

    @Test
    void trasladoPorVentasConFacturaAsociadaPasa() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgCamNRE().setiMotEmiNR(TiMotivTras.TRASLADO_POR_VENTAS);
        TgCamDEAsoc asociado = new TgCamDEAsoc();
        asociado.setiTipDocAso(TiTipDocAso.ELECTRONICO);
        asociado.setdCdCDERef("018006956310010010000006120211129175957146");
        de.setgCamDEAsocList(Collections.singletonList(asociado));

        SifenNotasValidator.validarNRE(de);
    }

    @Test
    void trasladoEntreLocalesConRucAjenoFalla() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgCamNRE().setiMotEmiNR(TiMotivTras.TRASLADO_ENTRE_LOCALES);
        de.getgDatGralOpe().getgDatRec().setdRucRec("99999999");

        assertMensaje(de, "propia empresa");
    }

    @Test
    void trasladoEntreLocalesConElRucPropioPasa() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgCamNRE().setiMotEmiNR(TiMotivTras.TRASLADO_ENTRE_LOCALES);

        SifenNotasValidator.validarNRE(de);
    }

    @Test
    void marcaDeVehiculoLargaFalla() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().getgTransp().getgVehTrasList().get(0).setdMarVeh("MERCEDES BENZ");

        assertMensaje(de, "10 caracteres");
    }

    @Test
    void sinTransporteFalla() {
        DocumentoElectronico de = deValido();
        de.getgDtipDE().setgTransp(null);

        assertMensaje(de, "tipo de transporte");
    }

    private static void assertMensaje(DocumentoElectronico de, String fragmento) {
        GraphQLException e = assertThrows(GraphQLException.class, () -> SifenNotasValidator.validarNRE(de));
        assertTrue(e.getMessage().toLowerCase().contains(fragmento.toLowerCase()),
                "mensaje inesperado: " + e.getMessage());
    }

    private static DocumentoElectronico deValido() {
        DocumentoElectronico de = new DocumentoElectronico();

        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        gOpeDE.setdInfoFisc("Documento emitido como Nota de Remision Electronica conforme RG 41/2014.");
        de.setgOpeDE(gOpeDE);

        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.NOTA_DE_REMISION_ELECTRONICA);
        de.setgTimb(gTimb);

        TgEmis gEmis = new TgEmis();
        gEmis.setdRucEm("80012345");
        TgDatRec gDatRec = new TgDatRec();
        gDatRec.setiNatRec(TiNatRec.CONTRIBUYENTE);
        gDatRec.setdRucRec("80012345");
        TdDatGralOpe datGralOpe = new TdDatGralOpe();
        datGralOpe.setgEmis(gEmis);
        datGralOpe.setgDatRec(gDatRec);
        de.setgDatGralOpe(datGralOpe);

        TgCamNRE gCamNRE = new TgCamNRE();
        gCamNRE.setiMotEmiNR(TiMotivTras.TRASLADO_POR_CONSIGNACION);
        gCamNRE.setiRespEmiNR(TiRespEmiNR.EMISOR_FACTURA);
        gCamNRE.setdFecEm(LocalDate.now().plusDays(1));

        TgCamItem item = new TgCamItem();
        item.setdCodInt("001");
        item.setdDesProSer("CAJA DE GASEOSAS");
        item.setcUniMed(TcUniMed.UNI);
        item.setdCantProSer(new BigDecimal("3"));

        TgCamSal gCamSal = new TgCamSal();
        gCamSal.setcCiuSal(1);
        TgCamEnt gCamEnt = new TgCamEnt();
        gCamEnt.setcCiuEnt(12);
        TgVehTras gVehTras = new TgVehTras();
        gVehTras.setdMarVeh("TOYOTA");
        TgTransp gTransp = new TgTransp();
        gTransp.setiTipTrans(TiTTrans.PROPIO);
        gTransp.setgCamSal(gCamSal);
        gTransp.setgCamEntList(new ArrayList<>(Collections.singletonList(gCamEnt)));
        gTransp.setgVehTrasList(new ArrayList<>(Collections.singletonList(gVehTras)));
        gTransp.setgCamTrans(new TgCamTrans());

        TgDtipDE gDtipDE = new TgDtipDE();
        gDtipDE.setgCamNRE(gCamNRE);
        gDtipDE.setgCamItemList(new ArrayList<>(Collections.singletonList(item)));
        gDtipDE.setgTransp(gTransp);
        de.setgDtipDE(gDtipDE);

        return de;
    }
}
