package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class RetiroPreGastoLineaInput {
    private Long monedaId;
    private Double monto;
}
