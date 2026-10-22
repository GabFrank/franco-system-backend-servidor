package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** Cómo cambió el precio de un ítem del pedido contra la última compra del producto. */
@Data
public class VariacionCostoPedidoItem {
    private Long pedidoItemId;
    private Double precioActual;
    private Double ultimoPrecio;
    /** Fracción: 0.12 = +12 %. Null sin compra anterior. */
    private Double variacion;
    /** SUBIO, BAJO, IGUAL o SIN_HISTORIAL. */
    private String direccion;
    /** Listo para mostrar; null si IGUAL o SIN_HISTORIAL. */
    private String texto;
}
