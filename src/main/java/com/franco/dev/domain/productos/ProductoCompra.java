package com.franco.dev.domain.productos;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Presentacion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductoCompra {

    private Double cantidad;
    private Double precio;
    private Pedido pedido;
    private LocalDateTime creadoEn;
    private Presentacion presentacionEnNota;

}