package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.TipoMovimientoPersonas;
import lombok.Data;

@Data
public class MovimientoPersonasInput {
    private Long id;
    private String observacion;
    private Long personaId;
    private TipoMovimientoPersonas tipo;
    private Long referenciaId;
    private Double valorTotal;
    private Boolean activo;
    private String vencimiento;
    private Long usuarioId;
}
