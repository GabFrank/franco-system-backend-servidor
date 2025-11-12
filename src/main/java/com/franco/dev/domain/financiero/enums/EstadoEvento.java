package com.franco.dev.domain.financiero.enums;

public enum EstadoEvento {
    PENDIENTE,          // Evento enviado, esperando procesamiento
    APROBADO,           // Evento aprobado por SIFEN
    RECHAZADO,          // Evento rechazado por SIFEN
    ERROR_ENVIO         // Error al enviar el evento
}

