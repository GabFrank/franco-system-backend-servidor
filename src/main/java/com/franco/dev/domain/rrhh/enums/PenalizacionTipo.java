package com.franco.dev.domain.rrhh.enums;

public enum PenalizacionTipo {
    TARDANZA,
    AUSENCIA,
    QUEJA_CLIENTE,
    AMBIENTE_LABORAL,
    DANIO_MATERIAL,
    COMISION_DESCUENTO,
    /**
     * Amonestacion disciplinaria. NO descuenta plata: se registra, se cuenta y se imprime
     * como acta firmable, pero queda fuera de todo calculo de liquidacion y de los KPIs de
     * penalizaciones. Reusa Penalizacion para no duplicar CRUD, paginado y seguridad.
     */
    ADVERTENCIA,
    OTRO
}
