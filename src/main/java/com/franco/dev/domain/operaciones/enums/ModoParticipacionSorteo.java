package com.franco.dev.domain.operaciones.enums;

/**
 * Cuantos tickets de participacion le tocan a una venta que alcanza el monto minimo del sorteo.
 */
public enum ModoParticipacionSorteo {
    /** 1 ticket si el monto computable alcanza el minimo. */
    UNA_POR_VENTA,
    /** floor(monto / minimo) tickets, limitado por tope_por_venta si esta cargado. */
    POR_MULTIPLO,
    /** cantidad_fija tickets si el monto computable alcanza el minimo. */
    CANTIDAD_FIJA
}
