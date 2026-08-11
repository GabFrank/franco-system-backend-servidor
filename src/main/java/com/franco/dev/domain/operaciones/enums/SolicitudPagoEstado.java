package com.franco.dev.domain.operaciones.enums;

import java.util.Arrays;
import java.util.List;

public enum SolicitudPagoEstado {
    PENDIENTE,  // Borrador: creada pero aún no finalizada/validada. NO pagable.
    SOLICITADO, // Validada y lista para pagar. Es lo que ven los diálogos de pago.
    PARCIAL,    // When a partial payment has been made
    CONCLUIDO,  // When the payment has been completed (pagada)
    CANCELADO;  // When the payment has been cancelled

    /**
     * Estados que representan deuda abierta (validada y aún no saldada por completo).
     * Fuente única para diálogos de pago y reportes CPP (aging / próximos vencimientos).
     * PENDIENTE queda fuera a propósito: es borrador, no pagable.
     */
    public static final List<SolicitudPagoEstado> DEUDA_ABIERTA = Arrays.asList(SOLICITADO, PARCIAL);
}
