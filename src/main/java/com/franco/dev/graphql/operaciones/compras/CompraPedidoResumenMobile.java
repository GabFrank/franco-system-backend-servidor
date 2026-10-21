package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** Un pedido que espera la factura del proveedor (etapa RECEPCION_NOTA abierta). */
@Data
public class CompraPedidoResumenMobile {
    private Long pedidoId;
    private Long proveedorId;
    private String proveedorNombre;
    private String proveedorRuc;
    private String fecha;
    private String monedaSimbolo;
    private Double totalPedido;
    private Integer cantidadItems;
    private Integer itemsPendientes;
    private Integer cantidadNotas;
    private String estadoEtapa;
}
