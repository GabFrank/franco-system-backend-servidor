package com.franco.dev.service.grafico.excel;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GraficoExcelFiltrosContext {
    String titulo;
    String filtroAnhos;
    String filtroMeses;
    String filtroRangoDias;
    String filtroSucursales;
    String filtroExtra;
}
