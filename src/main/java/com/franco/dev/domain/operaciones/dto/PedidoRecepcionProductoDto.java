package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PedidoRecepcionProductoDto {
    Producto producto;
    Double totalCantidadARecibirPorUnidad;
    Double totalCantidadRecibidaPorUnidad;

    public PedidoRecepcionProductoDto(Producto producto, Double totalCantidadARecibirPorUnidad, Double totalCantidadRecibidaPorUnidad) {
        this.producto = producto;
        this.totalCantidadARecibirPorUnidad = totalCantidadARecibirPorUnidad;
        this.totalCantidadRecibidaPorUnidad = totalCantidadRecibidaPorUnidad;
    }
}


// me quede en hacer funcionar este dto, falta configurar en angular y probar