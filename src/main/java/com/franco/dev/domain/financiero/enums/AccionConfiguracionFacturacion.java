package com.franco.dev.domain.financiero.enums;

/**
 * Que cambio sobre la politica de facturacion registra una fila del historial (issue filial #127).
 * Mismos valores que el CHECK de {@code financiero.configuracion_facturacion_historial.accion}
 * (V231.1) y que el enum del .graphqls.
 */
public enum AccionConfiguracionFacturacion {
    CREAR,
    MODIFICAR,
    ACTIVAR,
    DESACTIVAR,
    ELIMINAR
}
