package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class DevolucionItemInput {
    private Long id;
    private Long devolucionId;
    private Long productoId;
    private Long recepcionMercaderiaItemId;
    private Double cantidad;
    private String motivo;
    private String lote;
    private String creadoEn;
} 