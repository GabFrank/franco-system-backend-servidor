package com.franco.dev.domain.financiero.enums;

public enum FuentePago {
    CAJA_MAYOR, CUENTA_BANCARIA, CHEQUE,
    /** Línea de ajuste por redondeo/diferencia de cambio: no mueve efectivo ni banco. */
    AJUSTE
}
