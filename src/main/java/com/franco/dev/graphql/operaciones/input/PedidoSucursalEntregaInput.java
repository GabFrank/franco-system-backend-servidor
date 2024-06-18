package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PedidoSucursalEntregaInput {
    private Long id;

    private Long pedidoId;

    private Long sucursalId;

    private String creadoEn;

    private Long usuarioId;
}


