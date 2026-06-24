package com.franco.dev.graphql.grafico.input;

import lombok.Data;

import java.util.List;

@Data
public class GraficoExcelExportInput {
    private List<PeriodoGraficoInput> periodos;
    private List<Long> sucIds;
    private List<Long> usuarioIds;
    private List<Integer> anios;
    private String filtroAnhos;
    private String filtroMeses;
    private String filtroRangoDias;
    private String filtroSucursales;
    private String filtroExtra;
    private Integer limit;
    private Long familiaId;
    private Long subfamiliaId;
    private Boolean ascendente;
    private List<Long> productoIds;
}
