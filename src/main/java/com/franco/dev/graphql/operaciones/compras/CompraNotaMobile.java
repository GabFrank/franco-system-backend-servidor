package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** Una nota de recepción (factura) ya cargada en el pedido. */
@Data
public class CompraNotaMobile {
    private Long id;
    private Integer numero;
    /** 001-001-0067495 si viene de una factura electrónica; si no, el número a secas. */
    private String numeroCompleto;
    private Integer timbrado;
    private String cdc;
    private Boolean electronica;
    private String fecha;
    private String estado;
    private String estadoEtiqueta;
    private Double valor;
    private String monedaSimbolo;
    private Integer cantidadItems;
    /** Ítems que quedaron sin distribuir entre sucursales: hay que terminarlos en el desktop. */
    private Integer itemsSinDistribuir;
    private Boolean puedeEliminar;
}
