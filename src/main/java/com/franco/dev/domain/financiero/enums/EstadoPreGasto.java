package com.franco.dev.domain.financiero.enums;

public enum EstadoPreGasto {
    PENDIENTE, TRAMITE, AUTORIZADO, RECHAZADO, COMPLETADO, ENVIADO_A_TESORERIA,
    /** Pagado por tesorería (su SolicitudPago GASTO llegó a CONCLUIDO). Terminal. */
    PAGADO
}