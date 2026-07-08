package com.franco.dev.domain.operaciones.enums;

/**
 * Estados del documento de devolucion/salida de mercaderia.
 *
 * Flujo CON_PROVEEDOR:  PENDIENTE -> SEPARADO -> RETIRADO -> (CANJEADO | ACREDITADO)
 * Flujo SIN_PROVEEDOR:  PENDIENTE -> SEPARADO -> DESCARTADO
 * Cancelacion posible desde PENDIENTE o SEPARADO.
 *
 * Nota: el tipo Postgres operaciones.devolucion_estado incluye ademas el valor
 * legacy 'CONFIRMADA' (creado en V73.5, sin uso) que se conserva por la regla
 * aditiva de Flyway. No se mapea aca a proposito.
 */
public enum DevolucionEstado {
    PENDIENTE,
    SEPARADO,
    RETIRADO,
    CANJEADO,
    ACREDITADO,
    DESCARTADO,
    CANCELADA
}
