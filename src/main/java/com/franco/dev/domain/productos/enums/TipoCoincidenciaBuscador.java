package com.franco.dev.domain.productos.enums;

/**
 * Origen de la coincidencia en el buscador inteligente de productos.
 */
public enum TipoCoincidenciaBuscador {
    CODIGO_EXACTO,
    CODIGO_PREFIJO,
    /** Coincidencia parcial del código: tramo interno, terminación o sin ceros a la izquierda. */
    CODIGO_PARCIAL,
    TEXTO,
    ID
}
