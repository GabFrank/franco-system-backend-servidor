package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.productos.Producto;

public interface PedidoRecepcionProductoProjection {
    Producto getProducto();
    Double getTotalCantidadARecibirPorUnidad();
    Double getTotalCantidadRecibidaPorUnidad();
}
