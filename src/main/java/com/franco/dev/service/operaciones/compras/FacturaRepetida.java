package com.franco.dev.service.operaciones.compras;

import java.time.LocalDate;

/**
 * ¿Una nota ya cargada es esta misma factura? Solo para las cargas del celular: el desktop sigue como está.
 *
 * La factura electrónica trae timbrado y número, y la carga del celular los guarda en la nota. Pero el 84 % de las
 * notas cargadas a mano en el desktop no tiene timbrado (3.081 de 3.650 en bodega, 2026-10-21), así que con el
 * timbrado solo no alcanza: una nota sin timbrado del mismo proveedor y número es la misma factura si además coincide
 * la fecha o el monto. Si no coincide ninguno, puede ser otra factura con el mismo número (otro establecimiento o
 * punto de expedición): se avisa sin bloquear.
 */
public final class FacturaRepetida {

    public enum Veredicto { MISMA, POSIBLE, DISTINTA }

    /** Diferencia de monto que todavía es la misma factura (redondeos de la carga a mano). */
    static final double TOLERANCIA_MONTO = 0.01;

    private FacturaRepetida() {
    }

    /**
     * @param timbradoFactura timbrado de la factura electrónica
     * @param timbradoNota    timbrado de la nota ya cargada (null si se cargó sin él)
     */
    public static Veredicto comparar(Integer timbradoFactura, LocalDate fechaFactura, Double totalFactura,
                                     Integer timbradoNota, LocalDate fechaNota, Double valorNota) {
        if (timbradoNota != null && timbradoFactura != null) {
            return timbradoNota.equals(timbradoFactura) ? Veredicto.MISMA : Veredicto.DISTINTA;
        }
        boolean mismaFecha = fechaFactura != null && fechaFactura.equals(fechaNota);
        boolean mismoMonto = totalFactura != null && valorNota != null && totalFactura > 0
                && Math.abs(valorNota - totalFactura) / totalFactura <= TOLERANCIA_MONTO;
        return mismaFecha || mismoMonto ? Veredicto.MISMA : Veredicto.POSIBLE;
    }
}
