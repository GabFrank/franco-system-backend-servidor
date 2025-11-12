package com.franco.dev.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductoReportDto {
    private Long id;
    private String descripcion;
    private Double cantidad;
    private Double precioCosto;
    private Double precioVenta;
    private String codigoPrincipal;
}
