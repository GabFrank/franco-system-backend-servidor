package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Presentacion;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PedidoRecepcionProductoDto {
    Producto producto;
    Double totalCantidadARecibirPorUnidad;
    Double totalCantidadRecibidaPorUnidad;
    Double totalCantidadRechazadaPorUnidad;
    Double cantidadPendientePorUnidad;
    Boolean mostrarEnUnidadBase;
    Presentacion presentacionInicialSugerida;
    Double cantidadInicialPorPresentacion;

    public PedidoRecepcionProductoDto(Producto producto, Double totalCantidadARecibirPorUnidad, Double totalCantidadRecibidaPorUnidad) {
        this.producto = producto;
        this.totalCantidadARecibirPorUnidad = totalCantidadARecibirPorUnidad;
        this.totalCantidadRecibidaPorUnidad = totalCantidadRecibidaPorUnidad;
        this.totalCantidadRechazadaPorUnidad = 0.0;
        this.cantidadPendientePorUnidad = this.totalCantidadARecibirPorUnidad - this.totalCantidadRecibidaPorUnidad;
        this.mostrarEnUnidadBase = false;
    }

    public PedidoRecepcionProductoDto(Producto producto, Double totalCantidadARecibirPorUnidad, Double totalCantidadRecibidaPorUnidad, Double totalCantidadRechazadaPorUnidad) {
        this.producto = producto;
        this.totalCantidadARecibirPorUnidad = totalCantidadARecibirPorUnidad;
        this.totalCantidadRecibidaPorUnidad = totalCantidadRecibidaPorUnidad;
        this.totalCantidadRechazadaPorUnidad = totalCantidadRechazadaPorUnidad != null ? totalCantidadRechazadaPorUnidad : 0.0;
        this.cantidadPendientePorUnidad = (this.totalCantidadARecibirPorUnidad != null ? this.totalCantidadARecibirPorUnidad : 0.0)
                - (this.totalCantidadRecibidaPorUnidad != null ? this.totalCantidadRecibidaPorUnidad : 0.0)
                - this.totalCantidadRechazadaPorUnidad;
        this.mostrarEnUnidadBase = false;
    }
}


// me quede en hacer funcionar este dto, falta configurar en angular y probar