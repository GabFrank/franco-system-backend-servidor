package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class RecepcionCostoAdicionalInput {
    private Long id;
    private Long recepcionMercaderiaId;
    private String descripcion;
    private Double monto;
    private Long monedaId;
    private String creadoEn;
} 