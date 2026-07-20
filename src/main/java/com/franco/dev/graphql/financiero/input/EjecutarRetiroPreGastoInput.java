package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.util.List;

@Data
public class EjecutarRetiroPreGastoInput {
    private Long preGastoId;
    private Long sucursalId;
    private Long sucursalCajaId;
    private Long cajaId;
    private Long usuarioId;
    private Long gastoRegistroId;
    private List<RetiroPreGastoLineaInput> lineas;
}
