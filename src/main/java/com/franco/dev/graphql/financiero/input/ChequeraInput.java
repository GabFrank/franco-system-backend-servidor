package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChequeraInput {
    private Long id;
    private Long cuentaBancariaId;
    private String nombre;
    private String firmantes;
    private Double rangoDesde;
    private Double rangoHasta;
    private Long siguienteNumero;
    private com.franco.dev.domain.financiero.enums.EstadoChequera estado;
    private LocalDateTime fechaRetiro;
    private LocalDateTime creadoEn;
    private Long usuarioId;
} 