package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvolucionCostoResumen {
    private Double costoInicial;
    private Double costoFinal;
    private Double variacionPorcentual;
    private Double costoPromedioPonderado;
    private String periodoConMayorCosto;
    private Integer totalCompras;
    private Boolean hayDatos;
}
