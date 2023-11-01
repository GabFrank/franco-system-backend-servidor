package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LucroPorProductosDto {
    private Long productoId;
    private String descripcion;
    private Double costoUnitario;
    private Double cantidad;
    private Double totalVenta;
    private Double lucro;
    private Double percent;
    private Double ventaMedia;
    private Double margen;
}
