package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChequeraInput {
    private Long id;
    private Long cuentaBancariaId;
    private Double rangoDesde;
    private Double rangoHasta;
    private LocalDateTime fechaRetiro;
    private LocalDateTime creadoEn;
    private Long usuarioId;
} 