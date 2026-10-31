package com.franco.dev.domain.operaciones.enums;

/**
 * Forma en que se cerro una devolucion.
 *
 * NOTA_CREDITO: el proveedor emite una nota de credito (estado final ACREDITADO).
 * CANJE:        el proveedor entrega producto de reemplazo, que reingresa al stock
 *               con nuevo vencimiento (estado final CANJEADO).
 * DESCARTE:     salida sin proveedor que se da de baja como merma (estado final DESCARTADO).
 */
public enum TipoResolucionDevolucion {
    NOTA_CREDITO,
    CANJE,
    DESCARTE
}
