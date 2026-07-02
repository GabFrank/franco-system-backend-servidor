package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferenciaItemAlertaDTO {

    private Long transferenciaItemId;
    private Boolean alertaVencido;
    private Boolean alertaAveriado;
    private LocalDateTime fechaVencimientoReferencia;
}
