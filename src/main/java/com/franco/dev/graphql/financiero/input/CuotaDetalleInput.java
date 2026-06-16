package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CuotaDetalleInput {
    private Integer numeroCuota;
    private BigDecimal monto;
    private Boolean pagado;
}
