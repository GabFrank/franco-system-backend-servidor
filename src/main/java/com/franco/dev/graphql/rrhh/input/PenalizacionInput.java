package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.PenalizacionTipo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PenalizacionInput {
    private Long id;
    private Long funcionarioId;
    private Long jornadaId;
    private Long sucursalId;
    private PenalizacionTipo tipo;
    private String descripcion;
    private BigDecimal monto;
    private String fecha;
    private Boolean autoGenerada;
    private Boolean anulada;
    private Long registradoPorId;
}
