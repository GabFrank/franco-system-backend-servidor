package com.franco.dev.domain.financiero.enums;

/**
 * Como factura automaticamente el filial (issue filial #127). Mismos valores que el CHECK de
 * {@code financiero.configuracion_facturacion.modo} (V231.1) y que el enum del .graphqls.
 */
public enum ModoFacturacion {
    /** Toda venta con punto de venta se factura. */
    TODAS,
    /** Una de cada {@code ventasSinFactura + 1}: el facturaCountDown de siempre. */
    INTERVALO,
    /** Nunca automatica: solo cuando el cliente la pide. */
    A_PEDIDO
}
