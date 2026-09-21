package com.franco.dev.domain.financiero.enums;

/**
 * Motivo de emision de la nota de credito (gCamNCDE / iMotEmi). Espejo 1:1 por nombre de
 * {@code com.roshka.sifen.core.types.TiMotEmi}: se mapea con {@code valueOf(name())}, nunca por
 * codigo (el bug de la referencia).
 *
 * La descripcion del motivo en el XML (dDesMotEmi) la genera jsifenlib desde el enum; el campo
 * descripcion_motivo de la tabla es texto libre y solo sale en el KuDE.
 */
public enum MotivoEmisionNotaCredito {
    DEVOLUCION_Y_AJUSTES_DE_PRECIOS,
    DEVOLUCION,
    DESCUENTO,
    BONIFICACION,
    CREDITO_INCOBRABLE,
    RECUPERO_DE_COSTO,
    RECUPERO_DE_GASTO,
    AJUSTE_DE_PRECIO
}
