package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Cuantas veces entro un producto a una sucursal en un rango, y entre que fechas.
 *
 * Con estos tres numeros alcanza para la frecuencia de compra que calcula el diálogo de ítem:
 * el promedio de las diferencias entre compras consecutivas ordenadas telescopa a
 * {@code (ultima - primera) / (cantidad - 1)}, asi que las fechas del medio no hacen falta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComprasPorSucursalDto {
    private Long sucursalId;
    private Long cantidadCompras;
    private LocalDateTime primeraCompra;
    private LocalDateTime ultimaCompra;
}
