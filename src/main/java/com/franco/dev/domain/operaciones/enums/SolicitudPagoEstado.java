package com.franco.dev.domain.operaciones.enums;

public enum SolicitudPagoEstado {
    PENDIENTE,  // Borrador: creada pero aún no finalizada/validada. NO pagable.
    SOLICITADO, // Validada y lista para pagar. Es lo que ven los diálogos de pago.
    PARCIAL,    // When a partial payment has been made
    CONCLUIDO,  // When the payment has been completed (pagada)
    CANCELADO   // When the payment has been cancelled
}
