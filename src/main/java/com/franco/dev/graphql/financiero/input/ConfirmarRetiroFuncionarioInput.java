package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class ConfirmarRetiroFuncionarioInput {
    private Long preGastoId;
    private Long sucursalId;
    private String qrToken;
    private Long funcionarioPersonaId;
}
