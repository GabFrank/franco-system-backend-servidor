package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankingInflacionItem {
    private Long productoId;
    private String descripcion;
    private Double costoInicial;
    private Double costoFinal;
    private Double variacionPorcentual;
    private Integer periodos;
    private Integer totalCompras;
}
