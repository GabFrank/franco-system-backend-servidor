package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrestamoInput {
    private Long id;
    private Long funcionarioId;
    private String descripcion;
    private BigDecimal montoTotal;
    private Long monedaId;
    private String fechaInicio;
    private Integer cantidadCuotas;
    private String observacion;
    private Long usuarioId;
}
