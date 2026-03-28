package com.franco.dev.graphql.activos.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InmuebleInput {
    private Long id;
    private Long propietarioId;
    private String nombreAsignado;
    private Long paisId;
    private Long ciudadId;
    private String direccion;
    private String googleMapsUrl;
    private String codigoCatastral;
    private BigDecimal valorTasacion;
    private String situacionPago;
    private Long proveedorId;
    private Long monedaId;
    private BigDecimal montoTotal;
    private BigDecimal montoYaPagado;
    private Integer cantidadCuotas;
    private Integer diaVencimiento;
    private Long usuarioId;
}
