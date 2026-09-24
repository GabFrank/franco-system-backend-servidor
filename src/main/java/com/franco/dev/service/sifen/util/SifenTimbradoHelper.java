package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.TimbradoDetalle;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Helpers de timbrado y fecha para la construccion de un DE.
 *
 * Los usan las notas (remision y credito). La factura solo usa {@link #telefonoEmisor}:
 * {@code SifenService} sigue con {@code gTimb.setdEst("001")} hardcodeado, que es deuda anotada
 * aparte.
 */
public final class SifenTimbradoHelper {

    /** Codigo de establecimiento por defecto cuando la sucursal no lo tiene cargado. */
    public static final String ESTABLECIMIENTO_POR_DEFECTO = "001";

    private static final ZoneId ZONA_PY = ZoneId.of("America/Asuncion");

    /** Margen contra el reloj de SIFEN: una firma "adelantada" es rechazada. */
    private static final int MARGEN_SEGUNDOS = 10;

    private SifenTimbradoHelper() {
    }

    /**
     * Codigo de establecimiento del emisor, en el formato de 3 digitos que exige SIFEN (dEst).
     * Sale de {@code Sucursal.codigoEstablecimientoFactura}; si falta o no es numerico, "001".
     */
    public static String codigoEstablecimiento(Sucursal sucursal) {
        if (sucursal == null || sucursal.getCodigoEstablecimientoFactura() == null) {
            return ESTABLECIMIENTO_POR_DEFECTO;
        }
        String codigo = sucursal.getCodigoEstablecimientoFactura().trim();
        if (codigo.isEmpty()) {
            return ESTABLECIMIENTO_POR_DEFECTO;
        }
        try {
            return String.format("%03d", Integer.parseInt(codigo));
        } catch (NumberFormatException e) {
            return ESTABLECIMIENTO_POR_DEFECTO;
        }
    }

    /**
     * Telefono del emisor (dTelEmi): el del detalle del timbrado y, si esta vacio, el de la cabecera.
     * Nunca devuelve cadena vacia: SIFEN rechaza el lote con «0160 XML malformado: [El valor del
     * elemento: dTelEmi es invalido]» (paso el 2026-09-23 con el deposito 13, cuyo detalle no tenia
     * telefono y la cabecera si). Si faltan los dos devuelve null y el validador de notas lo corta.
     */
    public static String telefonoEmisor(TimbradoDetalle detalle) {
        if (detalle == null) {
            return null;
        }
        String telefono = recortado(detalle.getTelefono());
        if (telefono == null && detalle.getTimbrado() != null) {
            telefono = recortado(detalle.getTimbrado().getTelefono());
        }
        return telefono;
    }

    private static String recortado(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    /**
     * Fecha de firma segura: la del documento, pero nunca posterior a "ahora menos unos segundos"
     * en hora de Paraguay. SIFEN rechaza con "La fecha y hora de la firma digital es adelantada",
     * y ese rechazo obliga a reprocesar el DE (el filial tiene un metodo entero para eso).
     */
    public static LocalDateTime fechaFirmaSegura(LocalDateTime fechaDocumento) {
        LocalDateTime tope = LocalDateTime.now(ZONA_PY).minusSeconds(MARGEN_SEGUNDOS);
        if (fechaDocumento == null || fechaDocumento.isAfter(tope)) {
            return tope;
        }
        return fechaDocumento;
    }
}
