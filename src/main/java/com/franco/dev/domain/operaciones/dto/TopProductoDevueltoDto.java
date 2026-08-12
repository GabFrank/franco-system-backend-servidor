package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Producto mas devuelto: cantidad total y valor (costoUnitario x cantidad). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopProductoDevueltoDto {
    private Long productoId;
    private String descripcion;
    private Double cantidad;
    private Double valor;
}
