package com.franco.dev.domain.financiero.enums;

/** Qué se hizo con el cheque que tenía un número que se inutilizó (issue #603). */
public enum AccionChequeInutilizado {
    /** El número no tenía cheque. */
    NINGUNA,
    /** El cheque (suelto) se anuló con el mismo motivo. */
    ANULADO,
    /** El cheque pasó a otro número: la hoja se arruinó y el cheque real se escribió en otra. */
    RENUMERADO
}
