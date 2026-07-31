package com.franco.dev.domain.financiero.enums;

/**
 * Origen (dominio dueño) de un {@code MovimientoCajaVirtual}. Se persiste como
 * varchar (EnumType.STRING), no como enum de Postgres, para poder agregar valores
 * sin migración de tipo. Habilita el **bloqueo de anulación cross-módulo**: un
 * movimiento con origen distinto de {@link #MANUAL} solo se anula desde su módulo
 * dueño, nunca directamente desde la caja mayor.
 */
public enum OrigenMovimientoTipo {
    /** Movimiento operado directo en tesorería (ingreso/egreso/ajuste/transferencia manual). Anulable desde la caja. */
    MANUAL,
    /** Contra-movimiento de anulación (referencia al original). */
    ANULACION,
    // --- RRHH ---
    RRHH_VALE,
    RRHH_PRESTAMO,
    RRHH_AGUINALDO,
    RRHH_LIQUIDACION_SUELDO,
    RRHH_LIQUIDACION_FINAL,
    // --- Financiero / operaciones (se usan en fases posteriores) ---
    RETIRO_CAJA,
    VENTA_CREDITO_COBRO,
    DEVOLUCION,
    GASTO,
    ENTRADA_VARIA,
    OPERACION_FINANCIERA,
    PAGO_CPP,
    CHEQUE,
    ACREDITACION_POS
}
