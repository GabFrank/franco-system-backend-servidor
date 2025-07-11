package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.util.List;

@Data
public class SolicitudPagoMultipleRecepcionesInput {
    private Long proveedorId;
    private String descripcion;
    private List<Long> recepcionMercaderiaIds;
    private Long usuarioId;
} 