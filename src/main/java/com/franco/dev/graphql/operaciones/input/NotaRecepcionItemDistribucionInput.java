package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class NotaRecepcionItemDistribucionInput {
    private Long id;
    private Long notaRecepcionItemId;
    private Long sucursalEntregaId;
    private Double cantidad;
    private String creadoEn;
    private Long usuarioId;
    private Long sucursalInfluenciaId;
} 