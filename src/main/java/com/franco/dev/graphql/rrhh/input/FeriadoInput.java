package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FeriadoInput {
    private Long id;
    private String fecha;
    private String descripcion;
    private Boolean esNacional;
    private BigDecimal recargoPorcentaje;
    private Boolean activo;
    private Long usuarioId;
}
