package com.franco.dev.domain.financiero.enums;

public enum MovimientoClienteTipo {
    CARGO,           // aumenta la deuda (venta a crédito)
    PAGO,            // reduce la deuda (cobro)
    AJUSTE_POSITIVO,
    AJUSTE_NEGATIVO
}
