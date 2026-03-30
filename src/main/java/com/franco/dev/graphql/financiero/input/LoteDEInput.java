package com.franco.dev.graphql.financiero.input;

import java.time.LocalDateTime;

import com.franco.dev.domain.financiero.enums.EstadoLoteDE;

import lombok.Data;

@Data
public class LoteDEInput {
    private Long id;
    private EstadoLoteDE estado;
    private LocalDateTime fechaProcesado;
    private LocalDateTime fechaUltimoIntento;
    private Integer intentos;
    private String respuestaSifen;
    private String protocolo;
    private Long usuarioId;
}


