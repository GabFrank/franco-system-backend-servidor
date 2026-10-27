package com.franco.dev.domain.financiero.enums;

/**
 * Modulos que eligen su propia moneda en {@code financiero.configuracion_moneda_modulo} (V258.5).
 * Mismos valores que el CHECK de la columna {@code modulo} y que el enum del .graphqls.
 * Solo {@link #PDV} admite una fila por sucursal.
 */
public enum ModuloMoneda {
    /** Moneda con la que nacen los pedidos y las notas de recepcion. */
    COMPRAS,
    /** Moneda en la que se lleva el costo medio de los productos. */
    COSTOS,
    /** Moneda por defecto de los precios de venta. */
    PRECIOS,
    /** Moneda de la venta en el punto de venta; la unica que puede variar por sucursal. */
    PDV
}
