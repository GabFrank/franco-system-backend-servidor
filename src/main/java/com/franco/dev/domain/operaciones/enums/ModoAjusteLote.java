package com.franco.dev.domain.operaciones.enums;

/**
 * Qué está haciendo el operador cuando ajusta el stock de un lote.
 *
 * La diferencia no es cosmética: decide si la existencia total del producto cambia o no. Se elige
 * de forma explícita en la pantalla en vez de inferirse del saldo sin trazar, porque si no el mismo
 * número tecleado tendría efectos distintos según un saldo que el operador no ve ni controla.
 */
public enum ModoAjusteLote {

    /**
     * El stock ya estaba contado en la existencia pero no atribuido a ningún lote. Atribuirlo no
     * cambia el total: solo lo mueve del bucket sin trazar al lote, y por eso el movimiento
     * agregado que lo ancla va en cero.
     */
    ATRIBUIR,

    /**
     * La existencia estaba mal: rotura, faltante o mercadería encontrada. Corregirla cambia el
     * total del producto en la sucursal, igual que el ajuste de stock de siempre.
     */
    CORREGIR
}
