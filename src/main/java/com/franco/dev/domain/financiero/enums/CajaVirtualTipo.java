package com.franco.dev.domain.financiero.enums;

public enum CajaVirtualTipo {
    CAJA_MAYOR,
    /**
     * @deprecated Retirado (issue #220): el backend no le daba ninguna semántica. V274.5 pasó las cajas
     * existentes a CAJA_MAYOR y {@code saveCajaVirtual} ya no lo guarda. Sigue acá y en el
     * {@code .graphqls} (y en el enum de PostgreSQL) por compatibilidad con clientes viejos: se retira
     * en una versión posterior, con la estrategia de 2 versiones.
     */
    @Deprecated
    CAJA_CHICA
}
