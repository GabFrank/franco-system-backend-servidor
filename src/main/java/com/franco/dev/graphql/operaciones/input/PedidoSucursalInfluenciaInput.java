package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PedidoSucursalInfluenciaInput {
    private Long id;

    private Long pedidoId;

    private Long sucursalId;

    private LocalDateTime creadoEn;

    private Long usuarioId;
}


