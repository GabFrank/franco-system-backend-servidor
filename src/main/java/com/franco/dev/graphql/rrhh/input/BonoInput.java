package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BonoInput {
    private Long id;
    private Long funcionarioId;
    private BonoTipo tipo;
    private BigDecimal monto;
    private String fecha;
    private String motivo;
    private Boolean esRecurrente;
    private BonoFrecuencia frecuencia;
    private Long autorizadoPorId;
    private Long usuarioId;
}
