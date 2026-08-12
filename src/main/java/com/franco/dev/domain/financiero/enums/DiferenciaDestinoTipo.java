package com.franco.dev.domain.financiero.enums;

/** Qué hacer con la diferencia (sobra/falta) al cerrar una operación financiera. */
public enum DiferenciaDestinoTipo {
    /** No genera ningún movimiento adicional. */
    IGNORAR,
    /** Imputa la diferencia como un ajuste etiquetado "gasto por diferencia" en la caja. */
    GASTO,
    /** Imputa la diferencia como un ajuste etiquetado "vale por diferencia" en la caja. */
    VALE
}
