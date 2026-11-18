package com.franco.dev.domain.financiero.enums;

/**
 * Modulos que eligen su propia moneda en {@code financiero.configuracion_moneda_modulo} (V258.5).
 * Mismos valores que el CHECK de la columna {@code modulo} y que el enum del .graphqls.
 * Solo {@link #PDV} y {@link #FACTURACION} admiten una fila por sucursal ({@link #porSucursal()}).
 */
public enum ModuloMoneda {
    /** Moneda con la que nacen los pedidos y las notas de recepcion. */
    COMPRAS,
    /** Moneda en la que se lleva el costo medio de los productos. */
    COSTOS,
    /** Moneda por defecto de los precios de venta. */
    PRECIOS,
    /** Moneda de la venta en el punto de venta; puede variar por sucursal. */
    PDV,
    /**
     * Moneda en que sale la factura legal; puede variar por sucursal. Sin configuracion, la factura
     * sale en la moneda de la venta (no en la de la empresa): ver {@code MonedaModuloLector.configurada}.
     */
    FACTURACION;

    /** PDV y FACTURACION admiten una fila por sucursal; el resto es de toda la empresa. */
    public boolean porSucursal() {
        return this == PDV || this == FACTURACION;
    }
}
