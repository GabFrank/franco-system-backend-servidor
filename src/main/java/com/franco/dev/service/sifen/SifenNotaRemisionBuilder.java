package com.franco.dev.service.sifen;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.TipoTransporteNr;
import com.franco.dev.service.sifen.util.SifenGeografiaHelper;
import com.franco.dev.service.sifen.util.SifenTimbradoHelper;
import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.fields.request.de.*;
import com.roshka.sifen.core.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Arma el objeto DE de jsifenlib para una Nota de Remisión Electrónica (iTiDE 7).
 *
 * Portado de frc-efact (única implementación probada contra SIFEN), **sin** sus defectos:
 * el motivo se mapea por nombre de enum y no por código (allá {@code TiMotivTras.valueOf("1")}
 * siempre caía en traslado por ventas), el tipo de contribuyente del emisor sale de la property y
 * no está fijo, {@code iRespFlete} se deriva del tipo de transporte, {@code cCondNeg} se omite y el
 * chofer se informa según property. Y no hay fallback silencioso a Asunción: lo que falta lo rechaza
 * {@link SifenNotasValidator}.
 *
 * Solo construye el bean: no firma, no envía y no toca la base.
 */
@Slf4j
@Component
public class SifenNotaRemisionBuilder {

    @Value("${tipoContribuyenteEmisor:2}")
    private Integer tipoContribuyenteEmisor;

    /** Informar al chofer también en transporte propio. Si SIFEN empieza a rechazar, se apaga sin release. */
    @Value("${sifen.nre.chofer-en-propio:true}")
    private Boolean choferEnTransportePropio;

    @Value("${sifen.nre.info-fiscal:Documento emitido como Nota de Remision Electronica conforme RG 41/2014.}")
    private String infoFiscal;

    /**
     * @param cdcFacturaAsociada CDC de la factura que ampara el traslado, o null si no hay.
     */
    public DocumentoElectronico construir(NotaRemision nota, List<NotaRemisionItem> items,
                                          TimbradoDetalle timbradoDetalle, Sucursal sucursal,
                                          String cdcFacturaAsociada) {
        DocumentoElectronico de = new DocumentoElectronico();

        // Grupo A
        de.setdFecFirma(SifenTimbradoHelper.fechaFirmaSegura(nota.getFecha()));
        de.setdSisFact((short) 1);

        // Grupo B: dInfoFisc es OBLIGATORIO en una NRE (RG 41/2014)
        TgOpeDE gOpeDE = new TgOpeDE();
        gOpeDE.setiTipEmi(TTipEmi.NORMAL);
        gOpeDE.setdInfoFisc(infoFiscal);
        de.setgOpeDE(gOpeDE);

        // Grupo C: timbrado, con la serie propia de la nota de remisión
        TgTimb gTimb = new TgTimb();
        gTimb.setiTiDE(TTiDE.NOTA_DE_REMISION_ELECTRONICA);
        gTimb.setdNumTim(Integer.parseInt(timbradoDetalle.getTimbrado().getNumero().trim()));
        gTimb.setdEst(SifenTimbradoHelper.codigoEstablecimiento(sucursal));
        gTimb.setdPunExp(String.format("%03d", Integer.parseInt(timbradoDetalle.getPuntoExpedicion().trim())));
        gTimb.setdNumDoc(String.format("%07d", nota.getNumeroNotaRemision()));
        gTimb.setdFeIniT(timbradoDetalle.getTimbrado().getFechaInicio() != null
                ? timbradoDetalle.getTimbrado().getFechaInicio().toLocalDate()
                : LocalDate.now());
        de.setgTimb(gTimb);

        // Grupo D
        TdDatGralOpe datGralOpe = new TdDatGralOpe();
        datGralOpe.setdFeEmiDE(nota.getFecha() != null ? nota.getFecha() : LocalDateTime.now());

        TgOpeCom gOpeCom = new TgOpeCom();
        gOpeCom.setiTipTra(TTipTra.VENTA_MERCADERIA);
        gOpeCom.setiTImp(TTImp.IVA);
        gOpeCom.setcMoneOpe(CMondT.PYG);   // la NRE es siempre en guaraníes
        datGralOpe.setgOpeCom(gOpeCom);

        datGralOpe.setgEmis(construirEmisor(timbradoDetalle));
        datGralOpe.setgDatRec(construirReceptor(nota));
        de.setgDatGralOpe(datGralOpe);

        // Grupo E: gCamNRE + ítems sin valores + transporte
        de.setgDtipDE(construirDatosItems(nota, items, timbradoDetalle));

        // Grupo H: documento asociado, solo si el traslado tiene factura con CDC
        if (cdcFacturaAsociada != null && !cdcFacturaAsociada.trim().isEmpty()) {
            TgCamDEAsoc asociado = new TgCamDEAsoc();
            asociado.setiTipDocAso(TiTipDocAso.ELECTRONICO);
            asociado.setdCdCDERef(cdcFacturaAsociada.trim());
            de.setgCamDEAsocList(Collections.singletonList(asociado));
        }

        // Grupo F: una NRE NO lleva totales (gTotSub). No se setea a propósito.
        return de;
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
        gEmis.setdTelEmi(timbradoDetalle.getTelefono());
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

    /** El receptor de una NRE nunca es innominado: sale del snapshot que guarda la nota. */
    private TgDatRec construirReceptor(NotaRemision nota) {
        TgDatRec gDatRec = new TgDatRec();
        gDatRec.setdNomRec(nota.getReceptorNombre());
        gDatRec.setcPaisRec(PaisType.PRY);
        gDatRec.setdDirRec(nota.getReceptorDireccion());
        gDatRec.setcDepRec(SifenGeografiaHelper.departamento(nota.getReceptorDepartamento()));
        if (nota.getReceptorCodigoCiudad() != null) {
            gDatRec.setcCiuRec(nota.getReceptorCodigoCiudad());
        }
        gDatRec.setdDesCiuRec(nota.getReceptorCiudad());

        String documento = nota.getReceptorRuc() != null ? nota.getReceptorRuc().trim() : "";
        if (documento.contains("-")) {   // RUC: contribuyente
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
        } else {                          // cédula: no contribuyente, pero identificado
            gDatRec.setiNatRec(TiNatRec.NO_CONTRIBUYENTE);
            gDatRec.setiTiOpe(TiTiOpe.B2C);
            gDatRec.setiTipIDRec(TiTipDocRec.CEDULA_PARAGUAYA);
            gDatRec.setdNumIDRec(documento);
        }
        return gDatRec;
    }

    private TgDtipDE construirDatosItems(NotaRemision nota, List<NotaRemisionItem> items,
                                        TimbradoDetalle timbradoDetalle) {
        TgDtipDE gDtipDE = new TgDtipDE();

        // E500 - gCamNRE, obligatorio
        TgCamNRE gCamNRE = new TgCamNRE();
        gCamNRE.setiMotEmiNR(TiMotivTras.valueOf(nota.getMotivoEmision().name()));
        gCamNRE.setiRespEmiNR(TiRespEmiNR.valueOf(nota.getResponsableEmision().name()));

        // dKmR es OBLIGATORIO siempre, no solo cuando hay fecha estimada de factura. Lo dijo
        // SIFEN de producción el 2026-09-18, rechazando con
        // «0160 XML malformado: [Elemento esperado: dKmR dentro de: gCamNRE]».
        // El validador exige que venga cargado; el mínimo de 1 es la red para una nota vieja.
        int km = nota.getKmEstimado() != null && nota.getKmEstimado() > 0 ? nota.getKmEstimado() : 1;
        gCamNRE.setdKmR(km);

        LocalDate fechaEstimada = fechaEstimadaFactura(nota);
        if (fechaEstimada != null) {
            gCamNRE.setdFecEm(fechaEstimada);
        }
        gDtipDE.setgCamNRE(gCamNRE);

        // Ítems: descripción, cantidad y unidad. SIN gValorItem (E720) ni gCamIVA (E730).
        List<TgCamItem> gCamItemList = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            NotaRemisionItem item = items.get(i);
            TgCamItem gCamItem = new TgCamItem();
            gCamItem.setdCodInt(item.getCodigo() != null && !item.getCodigo().trim().isEmpty()
                    ? item.getCodigo().trim() : String.format("%03d", i + 1));
            gCamItem.setdDesProSer(item.getDescripcion());

            TcUniMed unidad = unidadMedida(item.getUnidadMedida());
            gCamItem.setcUniMed(unidad);
            gCamItem.setdCantProSer(unidad == TcUniMed.kg
                    ? item.getCantidad().setScale(3, RoundingMode.HALF_UP)
                    : item.getCantidad().setScale(0, RoundingMode.HALF_UP));
            gCamItemList.add(gCamItem);
        }
        gDtipDE.setgCamItemList(gCamItemList);

        gDtipDE.setgTransp(construirTransporte(nota, timbradoDetalle));
        return gDtipDE;
    }

    /**
     * dFecEm: la fecha estimada de la factura. SIFEN la acota al rango [emisión, emisión + 5 días] y
     * la exige cuando el motivo es traslado por ventas sin factura asociada.
     */
    private LocalDate fechaEstimadaFactura(NotaRemision nota) {
        if (nota.getFechaEstimadaFactura() == null) {
            return null;
        }
        LocalDate emision = nota.getFecha() != null ? nota.getFecha().toLocalDate() : LocalDate.now();
        LocalDate maxima = emision.plusDays(5);
        LocalDate fecha = nota.getFechaEstimadaFactura();
        if (fecha.isBefore(emision)) {
            log.warn("dFecEm {} es anterior a la emisión {}: se ajusta al mínimo", fecha, emision);
            return emision;
        }
        if (fecha.isAfter(maxima)) {
            log.warn("dFecEm {} excede los 5 días permitidos: se ajusta a {}", fecha, maxima);
            return maxima;
        }
        return fecha;
    }

    private TcUniMed unidadMedida(String unidad) {
        if (unidad == null || unidad.trim().isEmpty()) {
            return TcUniMed.UNI;
        }
        try {
            return TcUniMed.valueOf(unidad.trim());
        } catch (IllegalArgumentException e) {
            try {
                return TcUniMed.valueOf(unidad.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Unidad de medida '{}' desconocida para SIFEN: se informa UNI", unidad);
                return TcUniMed.UNI;
            }
        }
    }

    private TgTransp construirTransporte(NotaRemision nota, TimbradoDetalle timbradoDetalle) {
        TgTransp gTransp = new TgTransp();
        TipoTransporteNr tipo = nota.getTipoTransporte() != null
                ? nota.getTipoTransporte() : TipoTransporteNr.PROPIO;

        gTransp.setiTipTrans(TiTTrans.valueOf(tipo.name()));
        gTransp.setiModTrans(nota.getModalidadTransporte() != null
                ? TiModTrans.valueOf(nota.getModalidadTransporte().name())
                : TiModTrans.TERRESTRE);
        // Derivado del tipo de transporte, no fijo como en la referencia.
        gTransp.setiRespFlete(tipo == TipoTransporteNr.PROPIO
                ? TiRespFlete.TRANSPORTE_PROPIO : TiRespFlete.TERCERO);
        // cCondNeg se omite: es opcional para una operación interna (ver §10 del plan, sin verificar
        // contra SIFEN; si rechaza, se restaura CFR como en la referencia).
        gTransp.setcPaisDest(PaisType.PRY);

        // dIniTras es obligatorio en una NRE: jsifenlib revienta al serializar si falta
        // ("Cannot invoke LocalDate.toString() because this.dIniTras is null"). Si no vino, el
        // traslado empieza el dia de emision.
        LocalDate inicio = nota.getFechaInicioTraslado() != null
                ? nota.getFechaInicioTraslado()
                : (nota.getFecha() != null ? nota.getFecha().toLocalDate() : LocalDate.now());
        gTransp.setdIniTras(inicio);
        gTransp.setdFinTras(nota.getFechaFinTraslado() != null ? nota.getFechaFinTraslado() : inicio);

        TgCamSal gCamSal = new TgCamSal();
        gCamSal.setdDirLocSal(nota.getSalidaDireccion());
        gCamSal.setcDepSal(SifenGeografiaHelper.departamentoExigido(nota.getSalidaDepartamento(), "salida"));
        if (nota.getSalidaCodigoCiudad() != null) gCamSal.setcCiuSal(nota.getSalidaCodigoCiudad());
        gCamSal.setdDesCiuSal(nota.getSalidaCiudad());
        gTransp.setgCamSal(gCamSal);

        TgCamEnt gCamEnt = new TgCamEnt();
        gCamEnt.setdDirLocEnt(nota.getEntregaDireccion());
        gCamEnt.setcDepEnt(SifenGeografiaHelper.departamentoExigido(nota.getEntregaDepartamento(), "entrega"));
        if (nota.getEntregaCodigoCiudad() != null) gCamEnt.setcCiuEnt(nota.getEntregaCodigoCiudad());
        gCamEnt.setdDesCiuEnt(nota.getEntregaCiudad());
        gTransp.setgCamEntList(new ArrayList<>(Collections.singletonList(gCamEnt)));

        TgVehTras gVehTras = new TgVehTras();
        gVehTras.setdTiVehTras("VEHICULO");
        gVehTras.setdMarVeh(SifenGeografiaHelper.marcaVehiculo(nota.getVehiculoMarca()));
        if (nota.getVehiculoMatricula() != null && !nota.getVehiculoMatricula().trim().isEmpty()) {
            gVehTras.setdTipIdenVeh((short) 2);   // 2 = número de matrícula
            gVehTras.setdNroMatVeh(nota.getVehiculoMatricula().trim());
        }
        gTransp.setgVehTrasList(new ArrayList<>(Collections.singletonList(gVehTras)));

        gTransp.setgCamTrans(construirTransportista(nota, tipo, timbradoDetalle));
        return gTransp;
    }

    /**
     * SIFEN exige nombre, RUC y domicilio del transportista (dNomTrans, dRucTrans, dDomFisc) en toda
     * NRE: si faltan, rechaza con 0160 "XML malformado". En transporte **propio** el transportista
     * es la propia empresa, asi que se completan desde el timbrado cuando la nota no los trae.
     */
    private TgCamTrans construirTransportista(NotaRemision nota, TipoTransporteNr tipo,
                                              TimbradoDetalle timbradoDetalle) {
        TgCamTrans gCamTrans = new TgCamTrans();
        gCamTrans.setiNatTrans(TiNatRec.CONTRIBUYENTE);

        boolean propio = tipo == TipoTransporteNr.PROPIO;
        String nombre = nota.getTransportistaNombre();
        String rucTransportista = nota.getTransportistaRuc();
        String domicilio = nota.getTransportistaDireccion();
        if (propio) {
            if (nombre == null || nombre.trim().isEmpty()) {
                nombre = timbradoDetalle.getTimbrado().getRazonSocial();
            }
            if (rucTransportista == null || rucTransportista.trim().isEmpty()) {
                rucTransportista = timbradoDetalle.getTimbrado().getRuc();
            }
            if (domicilio == null || domicilio.trim().isEmpty()) {
                domicilio = timbradoDetalle.getDireccion();
            }
        }

        if (nombre != null && !nombre.trim().isEmpty()) {
            gCamTrans.setdNomTrans(nombre.trim());
        }
        if (domicilio != null && !domicilio.trim().isEmpty()) {
            gCamTrans.setdDomFisc(domicilio.trim());
        }
        String ruc = rucTransportista;
        if (ruc != null && !ruc.trim().isEmpty()) {
            String[] partes = ruc.trim().split("-");
            gCamTrans.setdRucTrans(partes[0]);
            if (partes.length > 1) {
                try {
                    gCamTrans.setdDVTrans(Short.parseShort(partes[1].trim()));
                } catch (NumberFormatException e) {
                    log.warn("DV del transportista '{}' no es numérico: no se informa", partes[1]);
                }
            }
        }
        boolean informarChofer = tipo == TipoTransporteNr.TERCERO
                || Boolean.TRUE.equals(choferEnTransportePropio);
        if (informarChofer) {
            if (nota.getChoferNombre() != null && !nota.getChoferNombre().trim().isEmpty()) {
                gCamTrans.setdNomChof(nota.getChoferNombre().trim());
            }
            if (nota.getChoferDocumento() != null && !nota.getChoferDocumento().trim().isEmpty()) {
                gCamTrans.setdNumIDChof(nota.getChoferDocumento().trim());
            }
            if (nota.getChoferDireccion() != null && !nota.getChoferDireccion().trim().isEmpty()) {
                gCamTrans.setdDirChof(nota.getChoferDireccion().trim());
            }
        }
        return gCamTrans;
    }

    /** Solo para tests: permite fijar las properties sin levantar Spring. */
    public void configurar(Integer tipoContribuyenteEmisor, Boolean choferEnTransportePropio, String infoFiscal) {
        this.tipoContribuyenteEmisor = tipoContribuyenteEmisor;
        this.choferEnTransportePropio = choferEnTransportePropio;
        this.infoFiscal = infoFiscal;
    }
}
