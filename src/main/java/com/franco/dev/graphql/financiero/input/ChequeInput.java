package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChequeInput {
    private Long id;
    private Long chequeraId;
    private Long pagoDetalleCuotaId;
    private Double numero;
    private LocalDateTime fechaEntrega;
    private LocalDateTime fechaPago;
    private String orden;
    private String concepto;
    private Boolean diferido;
    private Double total;
    private Long firmanteId;
    private LocalDateTime creadoEn;
    private Long usuarioId;
} 