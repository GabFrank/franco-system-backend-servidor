package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class ParteDivisionPedidoItemInput {
    private Long notaRecepcionId;
    /** En unidades de la presentación de la división. */
    private Double cantidad;
}
