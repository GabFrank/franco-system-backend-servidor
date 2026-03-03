package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class SolicitudPagoRecepcionInput {
    private Long id;
    private Long solicitudPagoId;
    private Long recepcionMercaderiaId;
    private Double montoAsignado;
    private String creadoEn;
} 