package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class DevolucionSaldoPreGastoInput {
    private Long preGastoId;
    private Long sucursalId;
    private Long cajaId;
    private Double vueltoGs;
    private Double vueltoRs;
    private Double vueltoDs;
    private Long usuarioId;
}
