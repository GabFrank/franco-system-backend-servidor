package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class PedidoItemDistribucionInput {
    private Long id;
    private Long pedidoItemId;
    private Long sucursalEntregaId;
    private Double cantidadSolicitada;
    private String creadoEn;
} 