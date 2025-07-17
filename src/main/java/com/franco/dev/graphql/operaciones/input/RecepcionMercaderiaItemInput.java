package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class RecepcionMercaderiaItemInput {
    private Long id;
    private Long recepcionMercaderiaId;
    private Long notaRecepcionItemId;
    private Long productoId;
    private Long presentacionRecibidaId;
    private Long sucursalEntregaId;
    private Double cantidadRecibida;
    private Double cantidadRechazada;
    private String vencimientoRecibido;
    private String lote;
    private Boolean esBonificacion;
    private String motivoRechazo;
    private String observaciones;
    private String creadoEn;
} 