package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/**
 * Pasar unidades de un lote a otro del mismo producto, en UNA sucursal, sin cambiar la existencia.
 *
 * Es la corrección de una venta, transferencia o recepción que se registró con el lote
 * equivocado. Los dos lotes tienen que existir ya (un lote nuevo se da de alta antes con
 * crearLoteProducto y se espera a que baje a la filial).
 */
@Data
public class ReasignarLoteInput {

    private Long productoId;

    private Long sucursalId;

    private Long loteOrigenId;

    private Long loteDestinoId;

    /** Unidades base que pasan del origen al destino. Mayor a cero. */
    private Double cantidad;

    /** Obligatorio: deja de trazar unidades en un lote y las traza en otro. */
    private String motivo;

    /** Opcional: venta, transferencia o recepción que salió con el lote equivocado. */
    private String referencia;

    /** Saldo del origen que vio el operador. Si cambió, se rechaza para recargar. */
    private Double saldoEsperado;

    /**
     * Saltea los avisos de sincronización con la filial y de ventas recientes. No saltea
     * las validaciones de saldo ni la de lotes ausentes en la filial.
     */
    private Boolean forzar;

    private Long usuarioId;
}
