package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoCostoPorPeriodo {
    private String periodo;
    private Double costoPromedio;
    private Double costoMinimo;
    private Double costoMaximo;
    private Double cantidad;
    private Integer cantidadCompras;
}
