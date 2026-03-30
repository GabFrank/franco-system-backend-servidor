package com.franco.dev.graphql.productos.input;

import lombok.Data;

@Data
public class ProductoProveedorInput {
    private Long id;
    private Long productoId;
    private Long proveedorId;
    private Long pedidoId;
    private Long usuarioId;
}
