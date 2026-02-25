package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import lombok.Data;

import java.util.List;

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
    private String fechaSolicitud;
    private String fechaPagoPropuesta;
    private String observaciones;
    private List<Long> notaRecepcionIds;
    private List<SolicitudPagoDetalleInput> detalles;
}

