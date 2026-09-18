package com.franco.dev.service.sifen;

import com.roshka.sifen.core.beans.DocumentoElectronico;
import com.roshka.sifen.core.fields.request.de.TgCamItem;
import com.roshka.sifen.core.fields.request.de.TgCamNRE;
import com.roshka.sifen.core.fields.request.de.TgTransp;
import com.roshka.sifen.core.types.CMondT;
import com.roshka.sifen.core.types.TTiDE;
import com.roshka.sifen.core.types.TiMotivTras;
import com.roshka.sifen.core.types.TiTipDocRec;
import graphql.GraphQLException;

/**
 * Valida el DE de una nota **antes** de firmarlo y mandarlo. Cada regla de acá es un rechazo real de
 * SIFEN: el costo de descubrirlas en producción es un documento rechazado con el número ya consumido.
 *
 * Portado de {@code validarDECompletoNRE} de frc-efact, más las dos reglas de geografía que allá se
 * tapaban con un fallback silencioso a Asunción (una ciudad inventada en un documento de traslado es
 * un dato fiscal falso, y el error vuelve como rechazo o como multa, no como excepción).
 */
public final class SifenNotasValidator {

    private SifenNotasValidator() {
    }

    /**
     * Reglas de la nota de credito. Menos que la remision porque el grueso lo comparte con la
     * factura, pero las tres que importan son fiscales: documento asociado, moneda y totales.
     */
    public static void validarNCE(DocumentoElectronico de) {
        if (de == null) {
            throw new GraphQLException("No se pudo construir el documento electrónico de la nota de crédito");
        }
        if (de.getgTimb() == null || de.getgTimb().getiTiDE() != TTiDE.NOTA_DE_CREDITO_ELECTRONICA) {
            throw new GraphQLException("El tipo de documento tiene que ser Nota de Crédito Electrónica");
        }
        if (de.getgDatGralOpe() == null || de.getgDatGralOpe().getgEmis() == null
                || de.getgDatGralOpe().getgDatRec() == null) {
            throw new GraphQLException("Faltan los datos del emisor o del receptor");
        }
        if (de.getgDtipDE() == null || de.getgDtipDE().getgCamNCDE() == null
                || de.getgDtipDE().getgCamNCDE().getiMotEmi() == null) {
            throw new GraphQLException("Falta el motivo de la nota de crédito");
        }
        if (de.getgDtipDE().getgCamItemList() == null || de.getgDtipDE().getgCamItemList().isEmpty()) {
            throw new GraphQLException("La nota de crédito necesita al menos un ítem");
        }
        // A diferencia de la remisión, acá los ítems SI llevan precio e IVA: sin ellos la nota
        // acredita cero.
        for (TgCamItem item : de.getgDtipDE().getgCamItemList()) {
            if (item.getgValorItem() == null) {
                throw new GraphQLException("Los ítems de una nota de crédito llevan precio");
            }
            if (item.getgCamIVA() == null) {
                throw new GraphQLException("Los ítems de una nota de crédito llevan IVA");
            }
        }
        if (de.getgTotSub() == null) {
            throw new GraphQLException("La nota de crédito tiene que llevar totales");
        }
        // El documento asociado es lo que convierte la nota en crédito DE esa factura.
        if (de.getgCamDEAsocList() == null || de.getgCamDEAsocList().isEmpty()) {
            throw new GraphQLException("La nota de crédito necesita el CDC de la factura asociada");
        }
        // Moneda: en guaraníes no se informa tipo de cambio; en extranjera es obligatorio.
        if (de.getgDatGralOpe().getgOpeCom() != null) {
            CMondT moneda = de.getgDatGralOpe().getgOpeCom().getcMoneOpe();
            java.math.BigDecimal cambio = de.getgDatGralOpe().getgOpeCom().getdTiCam();
            if (moneda != null && moneda != CMondT.PYG
                    && (cambio == null || cambio.signum() <= 0)) {
                throw new GraphQLException("Una nota de crédito en " + moneda
                        + " necesita el tipo de cambio de su factura");
            }
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    public static void validarNRE(DocumentoElectronico de) {
        if (de == null) {
            throw new GraphQLException("No se pudo construir el documento electrónico de la nota de remisión");
        }
        if (de.getgOpeDE() == null || de.getgOpeDE().getdInfoFisc() == null
                || de.getgOpeDE().getdInfoFisc().trim().isEmpty()) {
            throw new GraphQLException("La nota de remisión necesita el mensaje fiscal (dInfoFisc, RG 41/2014)");
        }
        if (de.getgTimb() == null || de.getgTimb().getiTiDE() != TTiDE.NOTA_DE_REMISION_ELECTRONICA) {
            throw new GraphQLException("El tipo de documento tiene que ser Nota de Remisión Electrónica");
        }
        if (de.getgDatGralOpe() == null || de.getgDatGralOpe().getgEmis() == null) {
            throw new GraphQLException("Faltan los datos del emisor");
        }
        if (de.getgDatGralOpe().getgDatRec() == null) {
            throw new GraphQLException("Faltan los datos del receptor");
        }
        if (de.getgDatGralOpe().getgDatRec().getiTipIDRec() == TiTipDocRec.INNOMINADO) {
            throw new GraphQLException("Una nota de remisión no admite receptor innominado: hay que identificarlo");
        }
        if (de.getgDtipDE() == null) {
            throw new GraphQLException("Faltan los datos del traslado");
        }

        TgCamNRE gCamNRE = de.getgDtipDE().getgCamNRE();
        if (gCamNRE == null) {
            throw new GraphQLException("Falta el grupo de la nota de remisión (gCamNRE)");
        }
        // dKmR es obligatorio SIEMPRE. SIFEN de producción rechaza el lote entero con
        // «0160 XML malformado: [Elemento esperado: dKmR dentro de: gCamNRE]».
        if (gCamNRE.getdKmR() <= 0) {
            throw new GraphQLException("Falta la distancia estimada del traslado en km: SIFEN la exige");
        }

        if (de.getgDtipDE().getgCamItemList() == null || de.getgDtipDE().getgCamItemList().isEmpty()) {
            throw new GraphQLException("La nota de remisión necesita al menos un ítem");
        }
        for (TgCamItem item : de.getgDtipDE().getgCamItemList()) {
            if (item.getgValorItem() != null) {
                throw new GraphQLException("Los ítems de una nota de remisión no llevan precio");
            }
            if (item.getgCamIVA() != null) {
                throw new GraphQLException("Los ítems de una nota de remisión no llevan IVA");
            }
        }

        // Una NRE no lleva totales: el grupo F no existe para este tipo de documento.
        if (de.getgTotSub() != null) {
            throw new GraphQLException("Una nota de remisión no lleva totales");
        }

        TgTransp gTransp = de.getgDtipDE().getgTransp();
        if (gTransp == null || gTransp.getiTipTrans() == null) {
            throw new GraphQLException("Falta el tipo de transporte");
        }
        if (gTransp.getgCamSal() == null) {
            throw new GraphQLException("Falta el local de salida");
        }
        if (gTransp.getgCamEntList() == null || gTransp.getgCamEntList().isEmpty()) {
            throw new GraphQLException("Falta el local de entrega");
        }
        if (gTransp.getgVehTrasList() == null || gTransp.getgVehTrasList().isEmpty()) {
            throw new GraphQLException("Falta el vehículo del traslado");
        }
        if (gTransp.getgCamTrans() == null) {
            throw new GraphQLException("Faltan los datos del transportista");
        }
        // SIFEN rechaza con 0160 ("XML malformado") si falta cualquiera de los tres.
        if (vacio(gTransp.getgCamTrans().getdNomTrans())) {
            throw new GraphQLException("Falta el nombre del transportista");
        }
        if (vacio(gTransp.getgCamTrans().getdRucTrans())) {
            throw new GraphQLException("Falta el RUC del transportista");
        }
        if (vacio(gTransp.getgCamTrans().getdDomFisc())) {
            throw new GraphQLException("Falta el domicilio fiscal del transportista");
        }
        // El chofer va completo o no va: SIFEN exige dNomChof, dNumIDChof y dDirChof juntos
        // (rechazo 0160 "Elemento esperado: dDirChof dentro de: gCamTrans").
        boolean algunDatoDeChofer = !vacio(gTransp.getgCamTrans().getdNomChof())
                || !vacio(gTransp.getgCamTrans().getdNumIDChof())
                || !vacio(gTransp.getgCamTrans().getdDirChof());
        if (algunDatoDeChofer) {
            if (vacio(gTransp.getgCamTrans().getdNomChof())) {
                throw new GraphQLException("Falta el nombre del chofer");
            }
            if (vacio(gTransp.getgCamTrans().getdNumIDChof())) {
                throw new GraphQLException("Falta el documento del chofer");
            }
            if (vacio(gTransp.getgCamTrans().getdDirChof())) {
                throw new GraphQLException("Falta la dirección del chofer");
            }
        }

        // Fechas del traslado: sin dIniTras, jsifenlib falla al serializar con un NullPointer
        // sobre this.dIniTras, que no dice nada de lo que falta.
        if (gTransp.getdIniTras() == null) {
            throw new GraphQLException("Falta la fecha de inicio del traslado");
        }

        // Sin fallback a Asunción: el código de ciudad es obligatorio y no puede ser 0.
        if (gTransp.getgCamSal().getcCiuSal() <= 0) {
            throw new GraphQLException("Falta la ciudad de salida del traslado");
        }
        if (gTransp.getgCamEntList().get(0).getcCiuEnt() <= 0) {
            throw new GraphQLException("Falta la ciudad de entrega del traslado");
        }

        // Traslado por ventas sin factura asociada: SIFEN exige la fecha estimada de la factura.
        if (gCamNRE.getiMotEmiNR() == TiMotivTras.TRASLADO_POR_VENTAS) {
            boolean tieneAsociado = de.getgCamDEAsocList() != null && !de.getgCamDEAsocList().isEmpty();
            if (!tieneAsociado && gCamNRE.getdFecEm() == null) {
                throw new GraphQLException("Un traslado por ventas sin factura asociada necesita la fecha "
                        + "estimada de la factura (hasta 5 días después de la emisión)");
            }
        }

        // Traslado entre locales: el receptor es la propia empresa.
        if (gCamNRE.getiMotEmiNR() == TiMotivTras.TRASLADO_ENTRE_LOCALES) {
            String rucEmisor = de.getgDatGralOpe().getgEmis().getdRucEm();
            String rucReceptor = de.getgDatGralOpe().getgDatRec().getdRucRec();
            if (rucReceptor == null || !rucReceptor.equals(rucEmisor)) {
                throw new GraphQLException("En un traslado entre locales el RUC del receptor tiene que ser "
                        + "el de la propia empresa");
            }
        }

        // dMarVeh: SIFEN rechaza con E962 si supera los 10 caracteres.
        String marca = gTransp.getgVehTrasList().get(0).getdMarVeh();
        if (marca != null && marca.length() > 10) {
            throw new GraphQLException("La marca del vehículo no puede superar los 10 caracteres: " + marca);
        }
    }
}
