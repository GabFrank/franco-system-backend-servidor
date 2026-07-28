package com.franco.dev.domain.operaciones.enums;

/**
 * Estado de un lote de producto.
 *
 * Solo los lotes LIBERADO entran en FEFO y se pueden vender. Los otros dos estados permiten sacar
 * un lote de circulación sin tocar el stock físico: la mercadería sigue estando en góndola y se
 * sigue contando en el inventario, simplemente no se puede vender.
 */
public enum EstadoLote {
    /** Disponible para la venta. Es el estado por defecto al recibir mercadería. */
    LIBERADO,
    /** Retenido a la espera de una definición (control de calidad, revisión). No se vende. */
    CUARENTENA,
    /** Bloqueado definitivamente (recall del proveedor, producto dañado). No se vende. */
    BLOQUEADO
}
