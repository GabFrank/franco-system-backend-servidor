package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BonoRecurrenteInput {
    private Long id;
    private Long funcionarioId;
    private BonoTipo tipo;
    private BigDecimal monto;
    private BonoFrecuencia frecuencia;
    private String motivo;
    private Boolean activo;
    private Long autorizadoPorId;
    private Long usuarioId;
}
