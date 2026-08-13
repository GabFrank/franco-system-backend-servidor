package com.franco.dev.domain.operaciones.enums;

/**
 * Forma de resolucion de una devolucion CON_PROVEEDOR.
 *
 * NOTA_CREDITO: el proveedor emite una nota de credito (estado final ACREDITADO).
 * CANJE:        el proveedor entrega producto de reemplazo, que reingresa al stock
 *               con nuevo vencimiento (estado final CANJEADO).
 */
public enum TipoResolucionDevolucion {
    NOTA_CREDITO,
    CANJE
}
