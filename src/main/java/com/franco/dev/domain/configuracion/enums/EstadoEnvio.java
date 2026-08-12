package com.franco.dev.domain.configuracion.enums;

public enum EstadoEnvio {
        PENDIENTE,
        EN_PROCESO,
        ENVIADO,
        ENTREGADO,
        FALLO_ENVIO,
        FALLO_DESTINO,
        /**
         * Se agotaron los reintentos sin lograr entregar. Estado terminal: la
         * cola de despacho no lo vuelve a tomar.
         */
        CANCELADA
}
