package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnteCuotaInput {
    private Long id;
    private Long enteFinancieroId;
    private Integer numeroCuota;
    private BigDecimal monto;
    private Boolean pagado;
    private String fechaVencimiento;
    private Long usuarioId;
}
