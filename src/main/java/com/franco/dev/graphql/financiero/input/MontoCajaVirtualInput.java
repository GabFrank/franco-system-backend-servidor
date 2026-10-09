package com.franco.dev.graphql.financiero.input;

import lombok.Data;

/** Un monto en una moneda, para los pedidos de caja mayor que llevan varias monedas a la vez. */
@Data
public class MontoCajaVirtualInput {
    private Long monedaId;
    private Double cantidad;
}
