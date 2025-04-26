package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import lombok.Data;

@Data
public class SolicitudPagoInput {
    private Long id;
    private String creadoEn;
    private Long usuarioId;
    private SolicitudPagoEstado estado;
    private TipoSolicitudPago tipo;
    private Long referenciaId;
}

