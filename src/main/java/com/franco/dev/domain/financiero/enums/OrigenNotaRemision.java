package com.franco.dev.domain.financiero.enums;

/**
 * De donde nace la nota de remision. Decide la precarga (prellenarNotaRemision) y que documento
 * asociado lleva el DE: una transferencia entre locales, una factura electronica, o nada.
 */
public enum OrigenNotaRemision {
    TRANSFERENCIA,
    FACTURA,
    MANUAL
}
