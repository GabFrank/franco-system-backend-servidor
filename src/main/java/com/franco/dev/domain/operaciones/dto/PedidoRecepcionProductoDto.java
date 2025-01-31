package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoRecepcionProductoDto {
    Long ProductoId;
    Double totalCantidadARecibirPorUnidad;
    Double totalCantidadRecibidaPorUnidad;
}


// me quede en hacer funcionar este dto, falta configurar en angular y probar