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
    private Long usuarioId;
}
