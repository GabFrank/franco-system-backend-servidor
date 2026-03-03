package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class SolicitudPagoDetalleInput {
    private Long id;
    private Long monedaId;
    private Long formaPagoId;
    private Double valor;
    private String fechaPago;
    private String observacion;
    private Double cotizacion;
    private Integer orden;
    private String fechaEmisionCheque;
    private String portador;
    private Boolean nominal;
    private Boolean diferido;
}
