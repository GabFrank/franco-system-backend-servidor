package com.franco.dev.domain.financiero.enums;

/**
 * Por qué se anuló un cheque (desktop #807). Un valor nuevo va también en {@code cheque.graphqls} y exige una
 * migración que redefina el CHECK {@code cheque_motivo_anulacion_tipo_check} (V289.1).
 */
public enum MotivoAnulacionCheque {
    /** El pago se hizo en efectivo y el cheque quedó sin efecto. */
    EFECTIVIZACION("Efectivización"),
    /** El banco lo rechazó por la firma. */
    FIRMA_NO_CONFORME("Firma no conforme"),
    /** Falla al imprimir: tinta, manchas, datos corridos. */
    MALA_IMPRESION("Mala impresión"),
    /** El cheque fue devuelto. */
    DEVOLUCION("Devolución de cheque"),
    PROVEEDOR_INCORRECTO("Proveedor incorrecto"),
    FECHA_INCORRECTA("Fecha incorrecta"),
    MONTO_INCORRECTO("Monto incorrecto"),
    /** Exige observación. */
    OTRO("Otro");

    private final String etiqueta;

    MotivoAnulacionCheque(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }
}
