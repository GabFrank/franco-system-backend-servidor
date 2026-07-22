package com.franco.dev.domain.rrhh.enums;

public enum VacacionVentaEstado {
    /** Pedida por el funcionario, todavia sin autorizar. */
    SOLICITADA,
    /** Autorizada: queda pendiente de cobro y la liquidacion la paga como HABER. */
    PENDIENTE,
    PAGADO,
    ANULADO
}
