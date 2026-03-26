package com.franco.dev.graphql.activos.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MuebleInput {
    private Long id;
    private Long propietarioId;
    private String identificador;
    private String descripcion;
    private Long familiaId;
    private Long tipoMuebleId;
    private Boolean consumeEnergia;
    private String consumoValor;
    private BigDecimal valorTasacion;
    private Long usuarioId;
}
