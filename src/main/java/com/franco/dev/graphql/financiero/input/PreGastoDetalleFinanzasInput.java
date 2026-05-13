package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PreGastoDetalleFinanzasInput {
    private Long id;
    private Long preGastoId;
    private Long sucursalId;
    private Long monedaId;
    private String formaPago;
    private BigDecimal monto;
}
