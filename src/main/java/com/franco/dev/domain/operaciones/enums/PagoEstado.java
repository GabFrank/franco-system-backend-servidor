package com.franco.dev.domain.operaciones.enums;

public enum PagoEstado {
    ABIERTO,   // When the payment is open but not yet processed
    PENDIENTE, // When the payment is pending approval or confirmation
    PARCIAL,   // When a partial payment has been made
    CONCLUIDO, // When the payment has been fully completed
    CANCELADO  // When the payment has been cancelled
}

