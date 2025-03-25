package com.franco.dev.domain.operaciones.enums;

public enum SolicitudPagoEstado {
    PENDIENTE, // When the payment is pending
    PARCIAL,   // When a partial payment has been made
    CONCLUIDO, // When the payment has been completed
    CANCELADO  // When the payment has been cancelled
}
