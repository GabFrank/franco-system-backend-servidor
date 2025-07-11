package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import lombok.Data;

@Data
public class SolicitudPagoInput {
    private Long id;
    private Long proveedorId;
    private Double montoTotal;
    private Long monedaId;
    private Long formaPagoId;
    private SolicitudPagoEstado estado;
    private String creadoEn;
    private Long usuarioId;
    private Long pagoId;
}

