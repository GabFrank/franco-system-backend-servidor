package com.franco.dev.domain.productos.dto;

import com.franco.dev.domain.financiero.Moneda;
import lombok.AllArgsConstructor;
import lombok.Data;

/** La moneda del precio principal de un producto, cuando no es la de la empresa (V260.5). */
@Data
@AllArgsConstructor
public class ProductoMonedaPrecio {
    private Long productoId;
    private Moneda moneda;
}
