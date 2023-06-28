package com.franco.dev.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductoIdAndCantidadDto {
    private Long productoId;
    private Double cantidad;
}
