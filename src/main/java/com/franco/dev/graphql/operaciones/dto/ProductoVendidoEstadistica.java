package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoVendidoEstadistica {
    private Long productoId;
    private String descripcion;
    private Double cantidad;
    private Double totalMonto;
    private Double porcentaje;
}
