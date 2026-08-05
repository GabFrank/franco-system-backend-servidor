package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvolucionCostoResponse {
    private List<ProductoCostoPorPeriodo> periodos;
    private EvolucionCostoResumen resumen;
}
