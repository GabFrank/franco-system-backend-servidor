package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.util.List;

/** División de un ítem del pedido en partes, cada una a una nota de recepción. */
@Data
public class DividirPedidoItemInput {
    private Long pedidoItemId;
    /** Presentación en la que vienen las cantidades de las partes. */
    private Long presentacionId;
    private List<ParteDivisionPedidoItemInput> partes;
}
