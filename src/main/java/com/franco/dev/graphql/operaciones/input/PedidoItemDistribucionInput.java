package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class PedidoItemDistribucionInput {
    private Long id;
    private Long pedidoItemId;
    private Long sucursalInfluenciaId;
    private Long sucursalEntregaId;
    private Double cantidadAsignada;
}