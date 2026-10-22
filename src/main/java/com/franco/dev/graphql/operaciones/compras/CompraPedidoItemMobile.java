package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** Un ítem del pedido con lo que falta facturar. Cantidades y precio en unidades base. */
@Data
public class CompraPedidoItemMobile {
    private Long pedidoItemId;
    private Long productoId;
    private String descripcion;
    private String presentacionDescripcion;
    private Double presentacionCantidad;
    private Double cantidadSolicitada;
    private Double cantidadFacturada;
    private Double cantidadPendiente;
    private Double precioUnitario;
    private Boolean esBonificacion;
    /** «Faltan 40 de 80 u.» o «Facturado completo». */
    private String pendienteTexto;
}
