package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.empresarial.Sucursal;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Helpers de timbrado y fecha para la construccion de un DE.
 *
 * Los usan las notas (remision y credito). La ruta de la factura NO se toca en este trabajo:
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
