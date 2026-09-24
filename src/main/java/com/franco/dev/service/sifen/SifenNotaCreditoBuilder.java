package com.franco.dev.service.sifen;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.domain.financiero.NotaCreditoItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.service.sifen.util.GCamIvaMapper;
import com.franco.dev.service.sifen.util.SifenGeografiaHelper;
import com.franco.dev.service.sifen.util.SifenTimbradoHelper;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Arma el objeto DE de jsifenlib para una Nota de Crédito Electrónica (iTiDE 5).
 *
 * Reglas fiscales que no se negocian:
 * - **La moneda se hereda de la factura**, con su tipo de cambio. Una NC en otra moneda es rechazo.
 * - `gCamCond` siempre **CONTADO** con una entrega inicial en efectivo: la NC no se financia.
 * - Los ítems **sí** llevan precio e IVA (al revés que la remisión), y el IVA lo arma
 *   {@link GCamIvaMapper}, el mismo que usa la factura.
 * - El documento asociado (`gCamDEAsoc`) con el CDC de la factura va en el **DE raíz**, no dentro
 *   del grupo de ítems.
 */
@Slf4j
@Component
public class SifenNotaCreditoBuilder {

    @Value("${tipoContribuyenteEmisor:2}")
    private Integer tipoContribuyenteEmisor;

    public DocumentoElectronico construir(NotaCredito nota, List<NotaCreditoItem> items,
                                          TimbradoDetalle timbradoDetalle, Sucursal sucursal,
                                          String cdcFactura) {
        DocumentoElectronico de = new DocumentoElectronico();

        de.setdFecFirma(SifenTimbradoHelper.fechaFirmaSegura(nota.getFecha()));
        de.setdSisFact((short) 1);

        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        de.setgOpeDE(gOpeDE);

        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.NOTA_DE_CREDITO_ELECTRONICA);
        gTimb.setdNumTim(Integer.parseInt(timbradoDetalle.getTimbrado().getNumero().trim()));
        gTimb.setdEst(SifenTimbradoHelper.codigoEstablecimiento(sucursal));
        gTimb.setdPunExp(String.format("%03d", Integer.parseInt(timbradoDetalle.getPuntoExpedicion().trim())));
        gTimb.setdNumDoc(String.format("%07d", nota.getNumeroNotaCredito()));
        gTimb.setdFeIniT(timbradoDetalle.getTimbrado().getFechaInicio() != null
                ? timbradoDetalle.getTimbrado().getFechaInicio().toLocalDate()
                : LocalDate.now());
        de.setgTimb(gTimb);

        TdDatGralOpe datGralOpe = new TdDatGralOpe();
        datGralOpe.setdFeEmiDE(nota.getFecha() != null ? nota.getFecha() : LocalDateTime.now());
        datGralOpe.setgOpeCom(construirOperacion(nota));
        datGralOpe.setgEmis(construirEmisor(timbradoDetalle));
        datGralOpe.setgDatRec(construirReceptor(nota));
        de.setgDatGralOpe(datGralOpe);

        de.setgDtipDE(construirDatosItems(nota, items));

        // Documento asociado: el CDC de la factura que se acredita. Va en el DE raíz.
        if (cdcFactura != null && !cdcFactura.trim().isEmpty()) {
            TgCamDEAsoc asociado = new TgCamDEAsoc();
            asociado.setiTipDocAso(TiTipDocAso.ELECTRONICO);
            asociado.setdCdCDERef(cdcFactura.trim());
            de.setgCamDEAsocList(new ArrayList<>(Collections.singletonList(asociado)));
        }

        // A diferencia de la remisión, la NC SI lleva totales: los calcula la librería.
        de.setgTotSub(new TgTotSub());
        return de;
    }

    private TgOpeCom construirOperacion(NotaCredito nota) {
        TgOpeCom gOpeCom = new TgOpeCom();
        gOpeCom.setiTipTra(TTipTra.VENTA_MERCADERIA);
        gOpeCom.setiTImp(TTImp.IVA);

        String moneda = nota.getMonedaExtranjera();
        if (esExtranjera(moneda)) {
            gOpeCom.setcMoneOpe(CMondT.valueOf(moneda.trim().toUpperCase()));
            gOpeCom.setdCondTiCam(TdCondTiCam.GLOBAL);
            if (nota.getTipoCambio() == null || nota.getTipoCambio().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "La factura está en " + moneda + " y la nota de crédito no tiene tipo de cambio");
            }
            gOpeCom.setdTiCam(nota.getTipoCambio().setScale(6, RoundingMode.HALF_UP));
        } else {
            // En guaraníes NO se informan dTiCam ni dCondTiCam.
            gOpeCom.setcMoneOpe(CMondT.PYG);
        }
        return gOpeCom;
    }

    private TgEmis construirEmisor(TimbradoDetalle timbradoDetalle) {
        TgEmis gEmis = new TgEmis();
        String[] ruc = timbradoDetalle.getTimbrado().getRuc().split("-");
        gEmis.setdRucEm(ruc[0]);
        gEmis.setdDVEmi(ruc.length > 1 ? ruc[1] : "");
        gEmis.setiTipCont(tipoContribuyenteEmisor != null && tipoContribuyenteEmisor == 1
                ? TiTipCont.PERSONA_FISICA : TiTipCont.PERSONA_JURIDICA);
        gEmis.setdNomEmi(timbradoDetalle.getTimbrado().getRazonSocial());
        gEmis.setdDirEmi(timbradoDetalle.getDireccion());
        gEmis.setdNumCas("0");
        gEmis.setdTelEmi(SifenTimbradoHelper.telefonoEmisor(timbradoDetalle));
        gEmis.setdEmailE(timbradoDetalle.getTimbrado().getEmail());
        gEmis.setcDepEmi(SifenGeografiaHelper.departamento(timbradoDetalle.getDepartamento()));
        if (timbradoDetalle.getCodigoCiudad() != null) {
            gEmis.setcCiuEmi(Integer.parseInt(timbradoDetalle.getCodigoCiudad().trim()));
        }
        gEmis.setdDesCiuEmi(timbradoDetalle.getCiudad());

        TgActEco actividad = new TgActEco();
        actividad.setcActEco(timbradoDetalle.getTimbrado().getCodActividadEconomicaPrincipal());
        actividad.setdDesActEco(timbradoDetalle.getTimbrado().getDescActividadEconomicaPrincipal());
        gEmis.setgActEcoList(new ArrayList<>(Collections.singletonList(actividad)));
        return gEmis;
    }

    /** Receptor: el snapshot que la nota copió de la factura. Una NC nunca es innominada. */
    private TgDatRec construirReceptor(NotaCredito nota) {
        TgDatRec gDatRec = new TgDatRec();
        gDatRec.setdNomRec(nota.getNombre() != null ? nota.getNombre() : "SIN NOMBRE");
        gDatRec.setcPaisRec(PaisType.PRY);
        gDatRec.setdDirRec(nota.getDireccion());

        String documento = nota.getRuc() != null ? nota.getRuc().trim() : "";
        if (documento.contains("-")) {
            String[] partes = documento.split("-");
            gDatRec.setiNatRec(TiNatRec.CONTRIBUYENTE);
            gDatRec.setiTiOpe(TiTiOpe.B2B);
            gDatRec.setiTiContRec(TiTipCont.PERSONA_JURIDICA);
            gDatRec.setdRucRec(partes[0]);
            if (partes.length > 1) {
                try {
                    gDatRec.setdDVRec(Short.parseShort(partes[1].trim()));
                } catch (NumberFormatException e) {
                    log.warn("DV '{}' del receptor no es numérico: no se informa", partes[1]);
                }
            }
        } else {
            gDatRec.setiNatRec(TiNatRec.NO_CONTRIBUYENTE);
            gDatRec.setiTiOpe(TiTiOpe.B2C);
            gDatRec.setiTipIDRec(documento.isEmpty() ? TiTipDocRec.INNOMINADO : TiTipDocRec.CEDULA_PARAGUAYA);
            gDatRec.setdNumIDRec(documento.isEmpty() ? "0" : documento);
        }
        return gDatRec;
    }

    private TgDtipDE construirDatosItems(NotaCredito nota, List<NotaCreditoItem> items) {
        TgDtipDE gDtipDE = new TgDtipDE();

        TgCamNCDE gCamNCDE = new TgCamNCDE();
        // Por nombre de enum, nunca por código: el bug de la referencia.
        gCamNCDE.setiMotEmi(TiMotEmi.valueOf(nota.getMotivoEmision().name()));
        gDtipDE.setgCamNCDE(gCamNCDE);

        // Condición de la operación: la NC se acredita al contado, sin cuotas.
        TgCamCond gCamCond = new TgCamCond();
        gCamCond.setiCondOpe(TiCondOpe.CONTADO);
        gCamCond.setgPaConEIniList(new ArrayList<>(Collections.singletonList(entregaInicial(nota))));
        gDtipDE.setgCamCond(gCamCond);

        List<TgCamItem> gCamItemList = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            NotaCreditoItem item = items.get(i);
            TgCamItem gCamItem = new TgCamItem();
            gCamItem.setdCodInt(String.format("%03d", i + 1));
            gCamItem.setdDesProSer(item.getDescripcion());
            gCamItem.setcUniMed(TcUniMed.UNI);
            gCamItem.setdCantProSer(item.getCantidad() != null
                    ? item.getCantidad() : BigDecimal.ONE);

            TgValorItem gValorItem = new TgValorItem();
            gValorItem.setdPUniProSer(precioUnitario(nota, item));
            TgValorRestaItem gValorRestaItem = new TgValorRestaItem();
            gValorItem.setgValorRestaItem(gValorRestaItem);
            gCamItem.setgValorItem(gValorItem);

            // Mismo mapeo de IVA que la factura (grupo E730, reglas de la NT13).
            gCamItem.setgCamIVA(GCamIvaMapper.construir(item.getIva()));

            gCamItemList.add(gCamItem);
        }
        gDtipDE.setgCamItemList(gCamItemList);
        return gDtipDE;
    }

    /** El precio unitario va en la moneda de la operación: si es extranjera, se convierte. */
    private BigDecimal precioUnitario(NotaCredito nota, NotaCreditoItem item) {
        BigDecimal precio = item.getPrecioUnitario() != null ? item.getPrecioUnitario() : BigDecimal.ZERO;
        if (esExtranjera(nota.getMonedaExtranjera())
                && nota.getTipoCambio() != null && nota.getTipoCambio().compareTo(BigDecimal.ZERO) > 0) {
            return precio.divide(nota.getTipoCambio(), 4, RoundingMode.HALF_UP);
        }
        return precio;
    }

    private TgPaConEIni entregaInicial(NotaCredito nota) {
        TgPaConEIni gPaConEIni = new TgPaConEIni();
        gPaConEIni.setiTiPago(TiTiPago.EFECTIVO);

        BigDecimal total = nota.getTotalFinal() != null ? nota.getTotalFinal() : BigDecimal.ZERO;
        if (esExtranjera(nota.getMonedaExtranjera())
                && nota.getTipoCambio() != null && nota.getTipoCambio().compareTo(BigDecimal.ZERO) > 0) {
            gPaConEIni.setcMoneTiPag(CMondT.valueOf(nota.getMonedaExtranjera().trim().toUpperCase()));
            gPaConEIni.setdTiCamTiPag(nota.getTipoCambio().setScale(6, RoundingMode.HALF_UP));
            gPaConEIni.setdMonTiPag(total.divide(nota.getTipoCambio(), 4, RoundingMode.HALF_UP));
        } else {
            gPaConEIni.setcMoneTiPag(CMondT.PYG);
            gPaConEIni.setdMonTiPag(total.setScale(0, RoundingMode.HALF_UP));
        }
        return gPaConEIni;
    }

    private static boolean esExtranjera(String moneda) {
        return moneda != null && !moneda.trim().isEmpty() && !"PYG".equalsIgnoreCase(moneda.trim());
    }

    /** Solo para tests. */
    public void configurar(Integer tipoContribuyenteEmisor) {
        this.tipoContribuyenteEmisor = tipoContribuyenteEmisor;
    }
}
