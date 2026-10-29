package com.franco.dev.graphql.grafico.dto;

import lombok.Data;

/** Cantidad de ventas a crédito (convenios) de una sucursal en el rango, por estado. */
@Data
public class ConvenioConteo {
    private int total;
    private int abiertas;
    private int finalizadas;
    private int canceladas;
    /** EN_MORA, INCOBRABLE o sin estado. */
    private int otras;
}
