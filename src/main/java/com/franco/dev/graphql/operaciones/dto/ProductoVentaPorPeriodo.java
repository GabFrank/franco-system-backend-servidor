package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoVentaPorPeriodo {
    private String periodo;
    private Double cantidad;
    private Double totalMonto;
}
