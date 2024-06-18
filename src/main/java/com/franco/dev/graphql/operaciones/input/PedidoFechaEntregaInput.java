package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class PedidoFechaEntregaInput {
    private Long id;
    private Long pedidoId;
    private String fechaEntrega;
    private String creadoEn;
    private Long usuarioId;
}


