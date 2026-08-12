package com.franco.dev.domain.operaciones.enums;

/**
 * Tipo de documento de devolucion/salida de mercaderia.
 *
 * SIN_PROVEEDOR: salida por averiado/vencido que NO se devuelve al proveedor.
 *                Baja de inventario + genera un Gasto (perdida contable).
 * CON_PROVEEDOR: devolucion a proveedor. Genera documento para el proveedor y
 *                se resuelve por nota de credito (ACREDITADO) o canje (CANJEADO).
 */
public enum TipoDevolucion {
    SIN_PROVEEDOR,
    CON_PROVEEDOR
}
