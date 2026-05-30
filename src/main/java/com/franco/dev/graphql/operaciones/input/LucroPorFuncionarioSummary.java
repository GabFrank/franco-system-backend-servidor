package com.franco.dev.graphql.operaciones.input;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LucroPorFuncionarioSummary {
    private Double cantidad;
    private Double costoTotal;
    private Double totalVenta;
    private Double lucro;
    private Double margen;
    private Double totalDescuento;
    private Double totalAumento;
    private Double ventaMedia;
    private Double costoUnitario;
}
